package com.photonspark.sparkmotors.client;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.Assembly;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value=AutoPropulsionAge.ID,dist=Dist.CLIENT)
public final class CarClient {
    public static final KeyMapping IGNITION=key("ignition",GLFW.GLFW_KEY_R),GARAGE=key("garage",GLFW.GLFW_KEY_G),LIGHTS=key("lights",GLFW.GLFW_KEY_H),
        PANELS=key("panels",GLFW.GLFW_KEY_O),REVERSE=key("reverse",GLFW.GLFW_KEY_Z),HORN=key("horn",GLFW.GLFW_KEY_B),CLUTCH=key("clutch",GLFW.GLFW_KEY_C);
    private static boolean reverse,performance;
    public static final KeyMapping INSTRUMENTS=key("instruments",GLFW.GLFW_KEY_V);
    private static int lastCar=-1;
    private static float lastYaw;
    private static final java.util.Map<Integer,CarAudio> sounds=new java.util.HashMap<>();
    public CarClient(IEventBus bus){
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->e.registerEntityRenderer(AutoPropulsionAge.CAR.get(),CarRenderer::new));
        bus.addListener((RegisterKeyMappingsEvent e)->{for(var key:new KeyMapping[]{IGNITION,GARAGE,LIGHTS,PANELS,REVERSE,HORN,CLUTCH,INSTRUMENTS})e.register(key);});
        bus.addListener((RegisterClientReloadListenersEvent e)->e.registerReloadListener((ResourceManagerReloadListener)resources->{stopSounds();CarMesh.reload(resources);}));
        bus.addListener((RegisterGuiLayersEvent e)->e.registerAboveAll(AutoPropulsionAge.id("dashboard"),(graphics,delta)->hud(graphics)));
        NeoForge.EVENT_BUS.addListener(CarClient::tick);
        NeoForge.EVENT_BUS.addListener((RenderHandEvent e)->{if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.getVehicle() instanceof CarEntity)e.setCanceled(true);});
        AutoPropulsionAge.openGarage=id->{var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getEntity(id) instanceof CarEntity car)mc.setScreen(new GarageScreen(car));};
        AutoPropulsionAge.openEngine=id->{var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getEntity(id) instanceof CarEntity car)mc.setScreen(new GarageScreen(car,4));};
    }
    public static int activeAudioVoices(){return sounds.values().stream().mapToInt(CarAudio::voices).sum();}
    public static void stopSounds(){sounds.values().forEach(CarAudio::stop);sounds.clear();}
    private static KeyMapping key(String name,int key){return new KeyMapping("key.sparkmotors."+name,key,"key.categories.sparkmotors");}
    public static void send(CarEntity car,int action,int a,int b){PacketDistributor.sendToServer(new CarPackets.Action(car.getId(),action,a,b));}
    private static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){stopSounds();return;}
        var nearby=mc.level.getEntitiesOfClass(CarEntity.class,mc.player.getBoundingBox().inflate(32));
        var ids=new java.util.HashSet<Integer>();for(var car:nearby){ids.add(car.getId());sounds.computeIfAbsent(car.getId(),id->new CarAudio(car)).tick();}
        sounds.entrySet().removeIf(e->{if(!ids.contains(e.getKey())){e.getValue().stop();return true;}return false;});
        CarEntity car=mc.player.getVehicle() instanceof CarEntity c?c:mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof CarEntity c?c:null;
        if(car==null){lastCar=-1;reverse=false;return;}
        if(mc.screen==null){
            while(INSTRUMENTS.consumeClick())performance=!performance;
            while(GARAGE.consumeClick())send(car,CarPackets.OPEN,0,0);
            while(IGNITION.consumeClick())send(car,CarPackets.IGNITION,0,0);
            while(LIGHTS.consumeClick())send(car,CarPackets.LIGHTS,0,0);
            while(PANELS.consumeClick())send(car,CarPackets.PANELS,0,0);
            while(HORN.consumeClick())send(car,CarPackets.HORN,0,0);
            while(REVERSE.consumeClick())if(car.horizontalSpeed()<.5)reverse=!reverse;
        }
        if(mc.player.getVehicle()==car){
            if(lastCar!=car.getId()){
                reverse=car.reverseSelected();lastCar=car.getId();lastYaw=car.getYRot();
                mc.player.setYRot(lastYaw);mc.player.yRotO=lastYaw;mc.player.setXRot(5);mc.player.xRotO=5;
            }
            float turn=net.minecraft.util.Mth.wrapDegrees(car.getYRot()-lastYaw);lastYaw=car.getYRot();
            mc.player.setYRot(mc.player.getYRot()+turn);mc.player.yRotO+=turn;
            int keys=reverse?8:0;float steer=0;
            if(mc.screen==null){
                if(mc.options.keyUp.isDown())keys|=1;
                if(mc.options.keyDown.isDown())keys|=2;
                if(mc.options.keyJump.isDown())keys|=4;
                if(CLUTCH.isDown())keys|=16;
                // Minecraft yaw and the existing rig basis turn right for positive input.
                steer=(mc.options.keyRight.isDown()?1:0)-(mc.options.keyLeft.isDown()?1:0);
            }else keys|=4;
            PacketDistributor.sendToServer(new CarPackets.Input(car.getId(),keys,steer));
        }else lastCar=-1;
    }
    private static void hud(GuiGraphics g){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null||!(mc.player.getVehicle() instanceof CarEntity car))return;
        // Bound HUD size independently of a high global GUI scale; keep the driving view open.
        float fit=(float)Math.min(1,2.0/mc.getWindow().getGuiScale());
        g.pose().pushPose();g.pose().scale(fit,fit,1);
        var r=CockpitInstruments.read(car);int w=290,x=((int)(g.guiWidth()/fit)-w)/2,y=(int)(g.guiHeight()/fit)-(performance?88:73);
        g.fill(x,y,x+w,y+(performance?61:46),0xD9101A23);g.fill(x,y,x+w,y+1,0xFF31C6C9);
        if(!r.powered()){g.drawString(mc.font,"INSTRUMENT POWER UNAVAILABLE",x+9,y+10,0xFFFFBA70,false);g.pose().popPose();return;}
        g.drawString(mc.font,String.format(java.util.Locale.ROOT,"%03.0f km/h   %s   %04.0f RPM",r.speed(),car.gear()<0?"R":"G"+car.gear(),r.rpm()),x+9,y+8,0xFFFFFFFF,false);
        if(car.powertrain().electric()){
            g.fill(x+1,y+2,x+w-1,y+(performance?61:46),0xFF101A23);
            String gear=car.reverseSelected()?"R":car.powertrain().hybrid()&&car.engineRunning()?"G"+car.gear():"D";
            g.drawString(mc.font,String.format(java.util.Locale.ROOT,"%03.0f km/h  %s  %+.1f kW",car.horizontalSpeed()*3.6,gear,car.packKw()),x+9,y+8,0xFFFFFFFF,false);
            String status=car.plugged()?"PLUGGED":!car.ignition()?"OFF":car.powertrain().hybrid()?(car.engineRunning()?"ENGINE":"ELECTRIC"):"READY";
            String supply=car.powertrain().hybrid()?String.format(java.util.Locale.ROOT,"SOC %.1f%%  Fuel %.1f L  %s",car.stateOfCharge()*100,car.fuel(),status):String.format(java.util.Locale.ROOT,"SOC %.1f%%  %.0f C  %s",car.stateOfCharge()*100,car.packTemperature(),status);
            g.drawString(mc.font,mc.font.plainSubstrByWidth(supply,w-18),x+9,y+21,car.stateOfCharge()<.1?0xFFFFA45C:0xFF97CDBE,false);
            if(performance)g.drawString(mc.font,car.powertrain().hybrid()?String.format(java.util.Locale.ROOT,"Engine %.0f RPM  Gen %.1f kW",car.rpm(),car.generatorKw()):String.format(java.util.Locale.ROOT,"%.0f motor RPM  Regen %.1f kW",car.motorRpm(),car.regenKw()),x+9,y+35,0xFF9EC9D4,false);
            g.drawString(mc.font,"R ready  S brake/regen  Space handbrake",x+9,y+(performance?49:34),0xFF9FB1BE,false);
            g.pose().popPose();return;
        }
        String coolant=Double.isFinite(r.coolant())?String.format(java.util.Locale.ROOT,"%.0f C",r.coolant()):"SENDER --";
        g.drawString(mc.font,mc.font.plainSubstrByWidth(String.format(java.util.Locale.ROOT,"Fuel %.1f L  Coolant %s  %s",r.fuel(),coolant,String.join(" ",r.warnings())),w-18),x+9,y+21,r.warnings().isEmpty()?0xFF97CDBE:0xFFFFA45C,false);
        if(performance)g.drawString(mc.font,String.format(java.util.Locale.ROOT,"Oil %s bar / %.0f C  %.1f V  Boost %.2f",Double.isFinite(r.oilPressure())?String.format(java.util.Locale.ROOT,"%.1f",r.oilPressure()):"--",car.oilTemperature(),r.voltage(),r.boost()),x+9,y+35,0xFF9EC9D4,false);
        g.drawString(mc.font,"R start  S brake  Space handbrake  V instruments",x+9,y+(performance?49:34),0xFF9FB1BE,false);
        g.pose().popPose();
    }
}
