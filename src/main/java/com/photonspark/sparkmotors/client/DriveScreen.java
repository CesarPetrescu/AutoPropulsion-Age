package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The diagram, wheel readings and conversion controls all use synchronized vehicle state. */
public final class DriveScreen extends WorkshopScreen {
    private final CarEntity car;
    private final Map<Button,java.util.function.BooleanSupplier> enabled=new LinkedHashMap<>();
    private int x,y,w,h,rx,rw,scroll;
    private static final int TEXT=0xFFE6F1F3,MUTED=0xFF9CB1BD,ACCENT=0xFF42D2C6;
    public DriveScreen(CarEntity car){super(Component.literal("Driveline setup"));this.car=car;}
    @Override public boolean isPauseScreen(){return false;}
    private boolean parked(){return car.horizontalSpeed()<.3&&!car.ignition();}
    private boolean conversionAllowed(){return parked()&&car.raised()&&MechanicalCapabilities.transmission(car.mechanics())>.01;}
    private void fit(DriveConfig config){CarClient.send(car,CarPackets.DRIVE_SETUP,config.packed(),config.frontPercent());}
    private Button button(String label,int bx,int by,int width,Runnable action,java.util.function.BooleanSupplier available,String tip){
        var b=Button.builder(Component.literal(label),ignored->action.run()).bounds(bx,by,width,22).build();
        b.setTooltip(Tooltip.create(Component.literal(tip)));addRenderableWidget(b);enabled.put(b,available);return b;
    }
    @Override protected void initWorkshop(){
        clearWidgets();enabled.clear();w=Math.min(790,width-16);h=420;
        int view=Math.min(h,height-16);scroll=Math.clamp(scroll,0,h-view);x=(width-w)/2;y=(height-view)/2-scroll;rx=x+w/2+10;rw=w/2-28;
        button("Garage",x+w-88,y+10,76,()->minecraft.setScreen(new GarageScreen(car)),()->true,"Return to the garage");
        int gap=6,bw=(rw-gap*2)/3;
        for(var layout:DriveConfig.Layout.values()){
            var preset=DriveConfig.preset(layout);
            button(layout.name(),rx+layout.ordinal()*(bw+gap),y+80,bw,()->fit(preset),()->conversionAllowed()&&!preset.equals(car.driveConfig()),
                "Fit the "+layout+" road preset. Requires the service jack and 8 iron ingots. Existing part wear and damage remain.");
        }
        String[] names={"Open diff","Limited slip","Locked diff"};
        for(var differential:DriveConfig.Differential.values())button(names[differential.ordinal()],rx,y+133+differential.ordinal()*29,rw,
            ()->fit(new DriveConfig(car.driveConfig().layout(),differential,car.driveConfig().frontPercent())),
            ()->conversionAllowed()&&car.driveConfig().differential()!=differential,
            "Open: wheels turn freely. Limited slip: transfers torque from a spinning wheel. Locked: strong coupling, useful for drift but scrubs in tight turns. Conversion costs 8 iron ingots.");
        button("Front -10%",rx,y+250,rw/2-3,()->fit(new DriveConfig(car.driveConfig().layout(),car.driveConfig().differential(),car.driveConfig().frontPercent()-10)),
            ()->parked()&&car.driveConfig().layout()==DriveConfig.Layout.AWD&&car.driveConfig().frontPercent()>20,"Adjust center torque split while parked with engine off. Range: 20–80% front.");
        button("Front +10%",rx+rw/2+3,y+250,rw/2-3,()->fit(new DriveConfig(car.driveConfig().layout(),car.driveConfig().differential(),car.driveConfig().frontPercent()+10)),
            ()->parked()&&car.driveConfig().layout()==DriveConfig.Layout.AWD&&car.driveConfig().frontPercent()<80,"More front torque changes traction and cornering balance; it does not add engine power.");
        button("Raise / lower jack",rx,y+h-56,rw,()->CarClient.send(car,CarPackets.JACK,0,0),
            ()->parked()&&car.getPassengers().isEmpty(),"Leave the car first. Survival requires a service jack. Lower the car before starting.");
    }
    @Override public void tick(){
        if(car.isRemoved()||minecraft.player==null||car.distanceToSqr(minecraft.player)>160){onClose();return;}
        enabled.forEach((button,available)->button.active=available.getAsBoolean());
    }
    @Override protected void renderWorkshop(GuiGraphics g,int mx,int my,float partial){
        clip(g,0,8,width,height-8);
        g.fill(0,0,width,height,0x99101922);g.fill(x,y,x+w,y+h,0xFA101B25);g.fill(x,y,x+w,y+2,ACCENT);
        g.drawString(font,"DRIVELINE / HANDLING",x+16,y+15,ACCENT,false);
        var drive=car.driveConfig();g.drawString(font,"INSTALLED: "+drive.layout()+" / "+drive.differential().name().replace('_',' '),x+16,y+42,TEXT,false);
        int center=x+w/4,frontY=y+104,rearY=y+226;
        boolean shaft=MechanicalCapabilities.transmission(car.mechanics())>.01;
        g.fill(center-3,frontY,center+3,rearY,drive.layout()!=DriveConfig.Layout.FWD&&shaft?ACCENT:0xFF324652);
        for(int axle=0;axle<2;axle++){
            boolean powered=shaft&&(axle==0?drive.frontPercent()>0:drive.frontPercent()<100);int by=axle==0?frontY:rearY;
            g.fill(center-66,by-3,center+66,by+3,powered?ACCENT:0xFF324652);
            for(int side=0;side<2;side++){
                int c=axle*2+side,bx=center+(side==0?-84:54);boolean present=car.mechanics().get("wheel."+ComponentSlot.CORNERS[c]+".tire")!=null;
                g.fill(bx,by-22,bx+30,by+22,!present?0xFF59383C:car.wheelSlip(c)>.25?0xFFC2783D:powered?0xFF267D78:0xFF405461);
                g.drawCenteredString(font,ComponentSlot.CORNERS[c].toUpperCase(Locale.ROOT),bx+15,by-4,TEXT);
                g.drawCenteredString(font,car.wheelContact(c)?"ROAD":"AIR",bx+15,by+28,MUTED);
                g.drawCenteredString(font,String.format(Locale.ROOT,"%.0f%% slip",car.wheelSlip(c)*100),bx+15,by+41,TEXT);
            }
        }
        g.drawCenteredString(font,"FRONT",center,frontY-37,MUTED);g.drawCenteredString(font,"REAR",center,rearY-37,MUTED);
        g.drawString(font,"ROAD PRESETS",rx,y+62,ACCENT,false);g.drawString(font,"AXLE DIFFERENTIALS",rx,y+116,ACCENT,false);
        g.drawString(font,String.format(Locale.ROOT,"Torque: %d%% front / %d%% rear",drive.frontPercent(),100-drive.frontPercent()),rx,y+228,TEXT,false);
        if(h>=390){
            g.drawWordWrap(font,Component.literal(switch(drive.layout()){
                case RWD->"Rear tires propel the car. Power can break rear grip; lift and countersteer to recover.";
                case FWD->"Front tires both steer and drive. Too much throttle in a turn uses up steering grip.";
                case AWD->"Both axles drive. The center split changes balance; all four tires still have finite grip.";
            }),rx,y+282,rw,MUTED);
            g.drawString(font,"ASSISTED HANDLING READINGS",x+16,y+h-95,ACCENT,false);
            g.drawString(font,String.format(Locale.ROOT,"%.0f km/h  Slip angle %.1f deg",car.horizontalSpeed()*3.6,Math.toDegrees(Math.atan2(car.lateralSpeed(),Math.max(.5,Math.abs(car.speed()))))),x+16,y+h-77,TEXT,false);
            g.drawString(font,"Space: rear handbrake  |  C: clutch",x+16,y+h-59,MUTED,false);
        }
        g.drawString(font,"Conversions: park, engine off, leave car, jack up. 8 iron ingots; part condition retained.",x+14,y+h-20,MUTED,false);
        super.renderWorkshop(g,mx,my,partial);
        g.disableScissor();
    }
    @Override protected boolean scrollWorkshop(double mx,double my,double dx,double dy){scroll=Math.clamp(scroll-(int)(dy*24),0,Math.max(0,436-height));init();return true;}
    @Override protected boolean clickWorkshop(double mx,double my,int button){return my>=8&&my<height-8&&super.clickWorkshop(mx,my,button);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
}
