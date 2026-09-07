package com.photonspark.autopropulsion.client;
import com.photonspark.autopropulsion.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=AutoPropulsion.ID,value=Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping IGNITION=new KeyMapping("key.autopropulsion.ignition",GLFW.GLFW_KEY_I,"key.categories.autopropulsion");
    public static final KeyMapping UP=new KeyMapping("key.autopropulsion.shift_up",GLFW.GLFW_KEY_R,"key.categories.autopropulsion");
    public static final KeyMapping DOWN=new KeyMapping("key.autopropulsion.shift_down",GLFW.GLFW_KEY_Z,"key.categories.autopropulsion");
    public static final KeyMapping CLUTCH=new KeyMapping("key.autopropulsion.clutch",GLFW.GLFW_KEY_C,"key.categories.autopropulsion");
    public static final KeyMapping GARAGE=new KeyMapping("key.autopropulsion.garage",GLFW.GLFW_KEY_G,"key.categories.autopropulsion");
    private static final java.util.Map<Integer,EngineLoop> sounds=new java.util.HashMap<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null){sounds.clear();return;}
        if(Boolean.getBoolean("autopropulsion.smoke")){SmokeClient.tick();return;}
        if(mc.player.getVehicle() instanceof VehicleEntity car&&car.getControllingPassenger()==mc.player) {
            if(mc.screen==null&&mc.isWindowActive()) {
                float t=mc.options.keyUp.isDown()?1:0,b=mc.options.keyDown.isDown()?1:0;
                float steer=(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0);
                PacketDistributor.sendToServer(new Packets.Input(car.getId(),t,b,steer,(CLUTCH.isDown()?1:0)|(mc.options.keyJump.isDown()?2:0)));
                if(IGNITION.consumeClick())send(car,1,0);if(UP.consumeClick())send(car,2,0);if(DOWN.consumeClick())send(car,3,0);if(GARAGE.consumeClick())send(car,0,0);
            } else PacketDistributor.sendToServer(new Packets.Input(car.getId(),0,1,0,3));
        }
        if(mc.level.getGameTime()%20==0) {
            sounds.entrySet().removeIf(e->e.getValue().isStopped());
            var cars=new java.util.ArrayList<VehicleEntity>();
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof VehicleEntity car&&car.running()&&car.distanceToSqr(mc.player)<48*48)cars.add(car);
            cars.sort(java.util.Comparator.comparingDouble(c->c.distanceToSqr(mc.player)));
            var audible=cars.stream().limit(8).toList();
            var keep=audible.stream().map(VehicleEntity::getId).collect(java.util.stream.Collectors.toSet());
            sounds.entrySet().removeIf(e->{if(!keep.contains(e.getKey())){mc.getSoundManager().stop(e.getValue());return true;}return false;});
            for(var car:audible)if(!sounds.containsKey(car.getId())){var loop=new EngineLoop(car);sounds.put(car.getId(),loop);mc.getSoundManager().play(loop);}
        }
    }
    public static void send(VehicleEntity car,int action,float value){PacketDistributor.sendToServer(new Packets.Action(car.getId(),action,value));}
    public static void openGarage(Packets.Garage packet){Minecraft.getInstance().setScreen(new GarageScreen(packet));}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(!Config.HUD.get()||mc.player==null||mc.screen!=null||!(mc.player.getVehicle() instanceof VehicleEntity car)||car.getControllingPassenger()!=mc.player)return;
        GuiGraphics g=event.getGuiGraphics();int x=(g.guiWidth()-246)/2,y=g.guiHeight()-76;
        g.fill(x,y,x+246,y+49,0xE616202B);g.fill(x,y,x+246,y+2,0xFFFFAD42);
        String gear=car.gear()==0?"N":car.gear()<0?"R":Integer.toString(car.gear());
        g.drawString(mc.font,String.format(java.util.Locale.ROOT,"%03.0f km/h    %s    %04.0f RPM",Math.abs(car.speed())*3.6,gear,car.rpm()),x+10,y+9,0xFFF1F5F8,false);
        g.fill(x+10,y+23,x+236,y+27,0xFF33424F);g.fill(x+10,y+23,x+10+(int)(226*Math.min(1,car.rpm()/8000)),y+27,car.rpm()>6500?0xFFFF6157:0xFFFFAD42);
        g.drawString(mc.font,String.format(java.util.Locale.ROOT,"%.1f L   %.0f C   +%.2f bar   %s",car.fuel(),car.temp(),car.boost(),car.running()?"RUN":"OFF"),x+10,y+34,car.health()<50?0xFFFF6157:0xFFB7C6D2,false);
    }
    @EventBusSubscriber(modid=AutoPropulsion.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(Content.VEHICLE.get(),VehicleRenderer::new);}
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(IGNITION);e.register(UP);e.register(DOWN);e.register(CLUTCH);e.register(GARAGE);}
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->VehicleRenderer.clearCache());}
    }
}
