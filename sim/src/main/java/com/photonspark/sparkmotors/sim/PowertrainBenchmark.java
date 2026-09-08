package com.photonspark.sparkmotors.sim;

import com.photonspark.sparkmotors.sim.electric.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Same 20-car fixed-step workload for each drivetrain. Excludes world, rendering and networking. */
public final class PowertrainBenchmark {
    private static volatile double checksum;
    public static void main(String[] args) throws Exception {
        var rows=new ArrayList<String>();
        for(var type:Powertrain.values())for(boolean moving:new boolean[]{false,true}){
            run(type,moving,200);
            rows.add(run(type,moving,600));
        }
        String json="{\"scope\":\"20 cars, 4 physics substeps; excludes Minecraft world/render/network\",\"java\":\""+System.getProperty("java.version")+"\",\"results\":[\n"+String.join(",\n",rows)+"\n]}\n";
        Path out=Path.of(args.length>0?args[0]:"build/reports/powertrain-performance.json");
        Files.createDirectories(out.toAbsolutePath().getParent());Files.writeString(out,json);System.out.print(json);
    }
    private static String run(Powertrain type,boolean moving,int ticks){
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        var road=new VehicleDynamics.State[20];var electric=new ElectricDynamics.State[20];var mechanics=new MechanicalState[20];
        var drive=DriveConfig.preset(DriveConfig.Layout.AWD);
        for(int c=0;c<20;c++){
            road[c]=new VehicleDynamics.State(moving?20:0,0,1,40,0,0);
            electric[c]=type.electric()?ElectricDynamics.State.initial(type,.55):null;
            mechanics[c]=PowertrainTopology.fresh(type,drive,EngineFamily.I4,Assembly.stock(),EnginePart.stock());
        }
        long[] times=new long[ticks];long allocated=0;double sum=0;
        double[] grip={.95,.95,.95,.95},travel=new double[4];boolean[] contacts={true,true,true,true};
        for(int t=0;t<ticks;t++){
            long bytes=bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime();
            for(int c=0;c<20;c++){
                var s=road[c];var m=mechanics[c];
                var setup=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,EnginePart.stock(),m.coolantTemperature(),1,m,drive).withPowertrain(type);
                var input=new VehicleDynamics.Input(moving?(t%160<120?.55:0):0,moving?Math.sin(t*.015+c)*.08:0,moving&&t%160>140,false,false,false);
                for(int sub=0;sub<4;sub++){
                    if(type.electric()){
                        var r=ElectricDynamics.step(type,electric[c],ElectricDynamics.Mode.AUTO,s.speed(),s.fuel(),s.engine(),s.wheels(),s.transmission(),true,input,setup,grip,contacts,travel,false,.0125,20);
                        s=r.road();electric[c]=r.electric();m=r.mechanics();setup=setup.withMechanics(m);
                    }else s=VehicleDynamics.step(s,true,input,setup,.95,true,.0125);
                }
                m=CircuitPhysics.step(m,s.rpm(),input.throttle(),s.engine().boost(),s.engine().mode()==EnginePhysics.Mode.RUNNING,s.engine().mode()==EnginePhysics.Mode.CRANKING,true,s.speed(),input.brake(),.05);
                if(!type.electric()||type.hybrid())m=InternalMechanics.step(m,setup.family(),s.rpm(),input.throttle(),s.engine().mode()==EnginePhysics.Mode.RUNNING,.05);
                mechanics[c]=WheelDynamics.wear(m,s.wheels(),s.speed(),.05);road[c]=s;sum+=s.speed()+m.coolantTemperature();
            }
            times[t]=System.nanoTime()-start;allocated+=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-bytes;
        }
        checksum=sum;Arrays.sort(times);
        return String.format(Locale.ROOT,"{\"type\":\"%s\",\"driving\":%s,\"ticks\":%d,\"meanMs\":%.4f,\"p95Ms\":%.4f,\"bytesPerCarTick\":%.0f,\"checksum\":%.3f}",type,moving,ticks,Arrays.stream(times).average().orElseThrow()/1e6,times[(int)(ticks*.95)]/1e6,allocated/(20.0*ticks),checksum);
    }
}
