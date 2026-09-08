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
    private static boolean reverse;
    private static int lastCar=-1;
    private static float lastYaw;
    private static final java.util.Map<Integer,CarAudio> sounds=new java.util.HashMap<>();
    public CarClient(IEventBus bus){
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->e.registerEntityRenderer(AutoPropulsionAge.CAR.get(),CarRenderer::new));
        bus.addListener((RegisterKeyMappingsEvent e)->{for(var key:new KeyMapping[]{IGNITION,GARAGE,LIGHTS,PANELS,REVERSE,HORN,CLUTCH})e.register(key);});
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
            while(GARAGE.consumeClick())send(car,CarPackets.OPEN,0,0);
            while(IGNITION.consumeClick())send(car,CarPackets.IGNITION,0,0);
            while(LIGHTS.consumeClick())send(car,CarPackets.LIGHTS,0,0);
            while(PANELS.consumeClick())send(car,CarPackets.PANELS,0,0);
            while(HORN.consumeClick())send(car,CarPackets.HORN,0,0);
            while(REVERSE.consumeClick())if(Math.abs(car.speed())<.5)reverse=!reverse;
        }
        if(mc.player.getVehicle()==car){
            if(lastCar!=car.getId()){
                reverse=car.gear()==-1;lastCar=car.getId();lastYaw=car.getYRot();
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
                steer=(mc.options.keyRight.isDown()?1:0)-(mc.options.keyLeft.isDown()?1:0);
            }else keys|=4;
            PacketDistributor.sendToServer(new CarPackets.Input(car.getId(),keys,steer));
        }else lastCar=-1;
    }
    private static void hud(GuiGraphics g){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null||!(mc.player.getVehicle() instanceof CarEntity car))return;
        int w=250,x=(g.guiWidth()-w)/2,y=g.guiHeight()-109;
        g.fill(x,y,x+w,y+68,0xE5101A23);g.fill(x,y,x+w,y+2,0xFF31C6C9);
        g.drawString(mc.font,"AUTOPROPULSION / SEDAN",x+10,y+8,0xFF7ECED0,false);
        g.pose().pushPose();g.pose().translate(x+10,y+23,0);g.pose().scale(2.2f,2.2f,1);
        g.drawString(mc.font,String.format(java.util.Locale.ROOT,"%03d",Math.round(Math.abs(car.speed())*3.6)),0,0,0xFFFFFFFF,false);g.pose().popPose();
        g.drawString(mc.font,"km/h",x+62,y+37,0xFFA2B5C0,false);
        g.drawString(mc.font,"GEAR "+(reverse?"R":car.gear()),x+102,y+24,0xFFFFFFFF,false);
        g.drawString(mc.font,Math.round(car.rpm())+" RPM",x+167,y+24,0xFFFFFFFF,false);
        g.fill(x+102,y+38,x+w-10,y+43,0xFF2A3C49);g.fill(x+102,y+38,x+102+(int)(128*Math.min(1,car.rpm()/car.limiter())),y+43,car.rpm()>car.limiter()*.9?0xFFF17C56:0xFF31C6C9);
        g.drawString(mc.font,String.format(java.util.Locale.ROOT,"FUEL %.1f L",car.fuel()),x+10,y+53,car.fuel()<5?0xFFFFA45C:0xFFD1E2E8,false);
        String status=!Assembly.canDrive(car.config())||!car.engineProblem().isEmpty()?"MISSING PARTS":car.health()<=0?"REPAIR REQUIRED":car.temperature()>=125?"ENGINE TOO HOT":car.ignition()?"ENGINE ON":"R: START ENGINE";
        g.drawString(mc.font,status,x+102,y+53,car.ignition()?0xFF73D8AA:0xFFFFC172,false);
        g.fill(x,y+68,x+w,y+83,0xE5101A23);
        g.drawString(mc.font,String.format(java.util.Locale.ROOT,"%.2f bar  AFR %.1f  Oil %.0f C",car.boost(),car.afr(),car.oilTemperature()),x+10,y+71,car.engineHealth()<50?0xFFFF8E60:0xFF7ECED0,false);
        g.drawCenteredString(mc.font,"W drive  S brake  C clutch  Z reverse  G garage",g.guiWidth()/2,y+89,0xFFDAE6EC);
    }
}
