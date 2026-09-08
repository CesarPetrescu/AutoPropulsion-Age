package com.photonspark.sparkmotors.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import java.lang.reflect.*;

/** Optional adapter to ElectricalAge's VERSIONED external-block electrical API.
 * A real grounded circuit load, not an FE converter. Integrates measured P*dt in ELN's
 * electrical-process callback; the vehicle can consume each collected joule only once.
 * Contract pinned to ElectricalAge cb11383d2d8266b6876ad1b1629cadde0d2c1706.
 */
public final class ElnChargingPort implements AutoCloseable {
    private static final System.Logger LOG=System.getLogger(ElnChargingPort.class.getName());
    private static boolean warned;
    private final Object sink,simulator,process;
    private final Method voltageMethod,powerMethod,resistanceMethod,highImpedance,unregister,removeProcess;
    private final double nominalV,maxW,maxA;
    private double bufferJ,discardedJ;
    private volatile double voltage,demandW;
    private boolean closed;
    private ElnChargingPort(Level level,BlockPos pos,double nominalV,double maxW,double maxA) throws ReflectiveOperationException {
        this.nominalV=nominalV;this.maxW=maxW;this.maxA=maxA;
        Class<?> api=Class.forName("mods.eln.api.v1.electrical.ElectricalIntegration");Object instance=api.getField("INSTANCE").get(null);
        int dimension=((Number)Class.forName("mods.eln.misc.DimensionIds").getMethod("id",Level.class).invoke(null,level)).intValue();
        sink=api.getMethod("createAndRegisterGroundedResistorSink",String.class,int.class,int.class,int.class,int.class,double.class,int.class)
            .invoke(instance,"sparkmotors.charger",dimension,pos.getX(),pos.getY(),pos.getZ(),1e12,1);
        Class<?> type=sink.getClass();voltageMethod=type.getMethod("getVoltage");powerMethod=type.getMethod("getPower");
        resistanceMethod=type.getMethod("setResistance",double.class);highImpedance=type.getMethod("highImpedance");unregister=type.getMethod("unregister");
        simulator=Class.forName("mods.eln.Eln").getField("simulator").get(null);
        Class<?> callback=Class.forName("mods.eln.sim.IProcess");
        process=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(proxy,method,args)->{
            if(method.getName().equals("process")){sample(((Number)args[0]).doubleValue());return null;}
            return switch(method.getName()){case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];case "toString"->"AutoPropulsion charger energy sampler";default->null;};
        });
        removeProcess=simulator.getClass().getMethod("removeElectricalProcess",callback);
        try{simulator.getClass().getMethod("addElectricalProcess",callback).invoke(simulator,process);}
        catch(ReflectiveOperationException failure){unregister.invoke(sink);throw failure;}
    }
    public static ElnChargingPort open(Level level,BlockPos pos,double nominalV,double maxW,double maxA){
        if(level.isClientSide||!ModList.get().isLoaded("eln"))return null;
        try{return new ElnChargingPort(level,pos,nominalV,maxW,maxA);}
        catch(ReflectiveOperationException|LinkageError e){warn(e);return null;}
    }
    /** Called at the ELN solver timestep, not a guessed Minecraft tick duration. */
    private synchronized void sample(double dt){
        if(closed||!Double.isFinite(dt)||dt<=0)return;
        try {
            double v=((Number)voltageMethod.invoke(sink)).doubleValue(),watts=((Number)powerMethod.invoke(sink)).doubleValue();
            voltage=Double.isFinite(v)?v:0;
            if(Double.isFinite(watts)&&watts>0){
                double energy=watts*dt;
                // Store only energy delivered inside the input safety envelope; otherwise it becomes heat.
                double accepted=voltage>=nominalV*.8&&voltage<=nominalV*1.1?Math.min(energy,Math.max(0,maxW*.1-bufferJ)):0;
                bufferJ+=accepted;discardedJ+=energy-accepted;
            }
            double demand=Math.min(demandW,Math.min(maxW,Math.max(0,voltage)*maxA));
            if(demand>0&&voltage>=nominalV*.8&&voltage<=nominalV*1.1)resistanceMethod.invoke(sink,Math.max(.001,voltage*voltage/demand));
            else highImpedance.invoke(sink);
        }catch(ReflectiveOperationException e){demandW=0;warn(e);}
    }
    public void request(double watts){demandW=Double.isFinite(watts)?Math.clamp(watts,0,maxW):0;}
    public double voltage(){return voltage;}
    public synchronized double availableJ(){return bufferJ;}
    public synchronized double discardedJ(){return discardedJ;}
    public synchronized boolean debit(double joules){
        if(closed||!Double.isFinite(joules)||joules<0||joules>bufferJ+1e-8)return false;
        bufferJ=Math.max(0,bufferJ-joules);return true;
    }
    @Override public synchronized void close(){
        if(closed)return;closed=true;demandW=0;discardedJ+=bufferJ;bufferJ=0;
        try{removeProcess.invoke(simulator,process);unregister.invoke(sink);}catch(ReflectiveOperationException|RuntimeException e){warn(e);}
    }
    private static void warn(Throwable e){if(!warned){warned=true;LOG.log(System.Logger.Level.WARNING,"ElectricalAge v1 charger interface unavailable; refusing energy transfer",e);}}
}
