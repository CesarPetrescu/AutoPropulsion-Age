package com.photonspark.autopropulsion.client;
import com.photonspark.autopropulsion.*;
import net.minecraft.client.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;

/** Opt-in dev harness only. Connects to loopback, drives through the real C2S path, captures real frames. */
public final class SmokeClient {
    private static int ticks;private static float maxSpeed,maxRpm;private static boolean garageSeen;
    private static String profile=System.getProperty("autopropulsion.smoke.config","default");
    public static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        ticks++;
        if(ticks==1){Config.HUD.set(!profile.equals("minimal"));Config.DETAIL.set(!profile.equals("minimal"));mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.options.hideGui=false;mc.player.setXRot(16);}
        if(!(mc.player.getVehicle() instanceof VehicleEntity car)){if(ticks>300)finish(false,"player not mounted on test vehicle");return;}
        maxSpeed=Math.max(maxSpeed,Math.abs(car.speed()));maxRpm=Math.max(maxRpm,car.rpm());
        if(ticks==35){mc.player.setYRot(-28);capture("01-showroom");}
        if(ticks==55)ClientEvents.send(car,0,0);
        if(ticks==80){garageSeen=mc.screen instanceof GarageScreen;if(!garageSeen){finish(false,"garage packet/screen failed");return;}capture("02-garage");}
        if(ticks==95){PacketDistributor.sendToServer(new Packets.Input(car.getId(),Float.NaN,0,0,0));}
        if(ticks==110&&mc.screen!=null)mc.screen.onClose();
        if(ticks==120)ClientEvents.send(car,1,0);
        if(ticks==125)ClientEvents.send(car,2,0);
        if(ticks>=130&&ticks<340){PacketDistributor.sendToServer(new Packets.Input(car.getId(),1,0,0,0));if(car.rpm()>5500&&car.gear()<5&&ticks%10==0)ClientEvents.send(car,2,0);}
        if(ticks==250)capture("03-driving-hud");
        if(ticks>=340&&ticks<410)PacketDistributor.sendToServer(new Packets.Input(car.getId(),0,1,0,3));
        if(ticks==380)capture("04-braking");
        if(ticks==415)finish(garageSeen&&maxSpeed>5&&maxRpm>1500&&Math.abs(car.speed())<.5&&car.getY()>50,"profile="+profile+" maxSpeed="+maxSpeed+" maxRpm="+maxRpm+" stoppedSpeed="+car.speed());
    }
    private static void capture(String name){Minecraft mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"autopropulsion-"+profile+"-"+name+".png",mc.getMainRenderTarget(),msg->AutoPropulsion.LOG.info("Screenshot: {}",msg.getString()));}
    private static void finish(boolean pass,String detail){
        String status=pass?"PASS":"FAIL";AutoPropulsion.LOG.info("AUTOPROPULSION_CLIENT_SMOKE_{} {}",status,detail);
        try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("smoke-result.txt"),status+" "+detail+"\n");}catch(Exception e){throw new RuntimeException(e);}
        Minecraft.getInstance().stop();
    }
    private SmokeClient(){}
}
