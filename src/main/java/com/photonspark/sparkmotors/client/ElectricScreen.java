package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.electric.ElectricDynamics;
import com.photonspark.sparkmotors.sim.PowertrainTopology;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Pack, traction motor and generator have separate units and separate readings. */
public final class ElectricScreen extends WorkshopScreen {
    private final CarEntity car;
    private final Map<Button,java.util.function.BooleanSupplier> enabled=new LinkedHashMap<>();
    private int x,y,w;
    private static final int INK=0xFFE7F0F4,MUTED=0xFF9FB1BE,ACCENT=0xFF42D2C6,POWER=0xFFFFB96A;
    public ElectricScreen(CarEntity car){super(Component.literal("Electric drive"));this.car=car;}
    @Override public boolean isPauseScreen(){return false;}
    private boolean parked(){return car.horizontalSpeed()<.3&&!car.ignition();}
    private void send(int action,int value){CarClient.send(car,action,value,0);}
    private void button(String text,int bx,int by,int bw,Runnable action,java.util.function.BooleanSupplier available,String tip){
        var b=Button.builder(Component.literal(text),ignored->action.run()).bounds(bx,by,bw,22).build();
        b.setTooltip(Tooltip.create(Component.literal(tip)));addRenderableWidget(b);enabled.put(b,available);b.active=available.getAsBoolean();
    }
    @Override protected void initWorkshop(){
        clearWidgets();enabled.clear();w=Math.min(780,width-16);x=(width-w)/2;y=(height-420)/2;
        button("Service parts",x+w-212,y+12,114,()->minecraft.setScreen(new ServiceScreen(car)),()->true,"Inspect, remove and install the actual drive units, inverter, CVs and HV components.");
        button("Garage",x+w-90,y+12,74,()->minecraft.setScreen(new GarageScreen(car)),()->true,"Return to the garage");
        int bw=(w-44)/3;
        button("READY / off",x+16,y+326,bw,()->send(CarPackets.IGNITION,0),()->car.powertrain().electric(),"Disconnect charging and lower the jack before driving.");
        button("Open / close hood",x+22+bw,y+326,bw,()->send(CarPackets.HOOD,0),()->car.horizontalSpeed()<.3,"Inspect the drive unit or generator.");
        button("Replace pack",x+28+2*bw,y+326,bw,()->send(CarPackets.REPLACE_BATTERY,0),()->parked()&&car.powertrain().electric()&&!car.plugged()&&car.hoodOpen()&&car.hoodProgress>=.95,"Carry a matching pack. Charge, temperature and wear stay with each pack.");
        button("80% limit",x+16,y+356,bw,()->send(CarPackets.CHARGE_TARGET,80),()->parked()&&car.powertrain().plugIn(),"Leave room for regenerative braking.");
        button("100% limit",x+22+bw,y+356,bw,()->send(CarPackets.CHARGE_TARGET,100),()->parked()&&car.powertrain().plugIn(),"Stop charging at full capacity; regen fades as the pack fills.");
        button("Hybrid mode",x+28+2*bw,y+356,bw,()->send(CarPackets.ELECTRIC_MODE,(car.electricMode().ordinal()+1)%3),()->parked()&&car.powertrain().hybrid(),"AUTO / ELECTRIC ONLY / CHARGE SUSTAIN. The engine powers a generator.");
    }
    @Override public void tick(){if(car.isRemoved()||minecraft.player==null||car.distanceToSqr(minecraft.player)>160){onClose();return;}enabled.forEach((b,f)->b.active=f.getAsBoolean());}
    @Override protected void renderWorkshop(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0x99101922);g.fill(x,y,x+w,y+420,0xFA101B25);g.fill(x,y,x+w,y+2,ACCENT);
        g.drawString(font,"ELECTRIC DRIVE",x+16,y+16,ACCENT,false);
        g.drawString(font,car.powertrain().title,x+16,y+43,INK,false);
        if(!car.powertrain().electric()){
            g.drawWordWrap(font,Component.literal("This car has a combustion drivetrain. Craft an electric or hybrid vehicle crate to use a traction battery."),x+16,y+76,w-32,MUTED);
        }else{
            int col=x+w/2+8,cw=w/2-24;
            g.drawString(font,"MOTOR CURVES / rated envelope",x+16,y+72,ACCENT,false);
            int cx=x+16,cy=y+97,bw=w/2-40,bh=130;
            g.fill(cx,cy,cx+bw,cy+bh,0xFF182F3F);
            for(int i=0;i<bw;i++){
                double rpm=18000.0*i/(bw-1),torque=ElectricDynamics.motorTorque(car.powertrain(),rpm),kw=torque*rpm*Math.PI/30000;
                int ty=cy+bh-1-(int)(torque/car.powertrain().torqueNm*(bh-8)),py=cy+bh-1-(int)(kw/car.powertrain().motorKw*(bh-8));
                g.fill(cx+i,ty,cx+i+1,ty+2,ACCENT);g.fill(cx+i,py,cx+i+1,py+2,POWER);
            }
            g.drawString(font,"0                    Motor RPM 18,000",cx,cy+bh+7,MUTED,false);
            g.drawString(font,String.format(Locale.ROOT,"Torque %.0f Nm",car.powertrain().torqueNm),cx,cy+bh+25,ACCENT,false);
            g.drawString(font,String.format(Locale.ROOT,"Power %.0f kW",car.powertrain().motorKw),cx+bw/2,cy+bh+25,POWER,false);
            for(int axle=0;axle<2;axle++){
                String name=axle==0?"Front":"Rear";var motor=car.mechanics().get(PowertrainTopology.unit("motor",axle));var inverter=car.mechanics().get(PowertrainTopology.unit("inverter",axle));
                boolean driven=axle==0?PowertrainTopology.front(car.driveConfig()):PowertrainTopology.rear(car.driveConfig());
                String reading=!driven?"Not driven by this layout":motor==null||inverter==null?"Drive unit incomplete":String.format(Locale.ROOT,"%.0f C motor / %.0f C inverter",motor.temperature(),inverter.temperature());
                g.drawString(font,font.plainSubstrByWidth(name+": "+reading,bw),cx,y+274+axle*15,MUTED,false);
            }
            g.drawString(font,PowertrainTopology.liveHv(car.mechanics())?"HV control circuit: available":"HV control circuit: open",cx,y+305,MUTED,false);
            String[] labels={"Drive state","Charge / target","Pack power (+ draw)","Voltage / current","Pack / condition","Motor / inverter","Motor speed","Regen / generator","Generator crank","Charging","Hybrid control"};
            String[] values={car.plugged()?"PLUGGED":car.ignition()?"READY":"OFF",String.format(Locale.ROOT,"%.1f%% / %d%%",car.stateOfCharge()*100,car.chargeTarget()),String.format(Locale.ROOT,"%+.1f kW",car.packKw()),String.format(Locale.ROOT,"%.0f V / %.1f A",car.packVoltage(),car.packCurrent()),String.format(Locale.ROOT,"%.0f C / %.0f%%",car.packTemperature(),car.packHealth()*100),String.format(Locale.ROOT,"%.0f C / %.0f C",car.motorTemperature(),car.inverterTemperature()),String.format(Locale.ROOT,"%.0f RPM",car.motorRpm()),String.format(Locale.ROOT,"%.1f / %.1f kW",car.regenKw(),car.generatorKw()),car.powertrain().hybrid()?String.format(Locale.ROOT,"%.0f RPM",car.rpm()):"None",car.plugged()?String.format(Locale.ROOT,"%.2f kW input",car.chargeKw()):"Unplugged",car.powertrain().hybrid()?car.electricMode().name().replace('_',' '):"Battery electric"};
            g.drawString(font,"ASSISTED LIVE TELEMETRY",col,y+72,ACCENT,false);
            for(int i=0;i<labels.length;i++){int by=y+96+i*19;g.drawString(font,labels[i],col,by,MUTED,false);g.drawString(font,values[i],col+cw-font.width(values[i]),by,INK,false);}
        }
        g.drawString(font,"Cable: charger first, then car. Sneak-click charger to unplug. S: brake / regen.",x+16,y+399,MUTED,false);
        super.renderWorkshop(g,mx,my,partial);
    }
}
