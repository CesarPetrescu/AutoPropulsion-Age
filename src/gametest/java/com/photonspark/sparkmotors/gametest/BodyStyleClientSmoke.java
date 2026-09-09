package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.charging.*;
import com.photonspark.sparkmotors.client.*;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Actual in-world renderer/GUI/input verification, never included in the installable JAR. */
@EventBusSubscriber(modid="sparkmotors",value=Dist.CLIENT)
public final class BodyStyleClientSmoke {
    private static final long START=System.nanoTime();
    private static boolean creating,spawnPending,prepared,done;
    private static int job,ticks,finishTicks;
    private static volatile int carId=-1;
    private static volatile String failure;
    private static volatile Vec3 origin,lookTarget;
    private static volatile BlockPos chargerPos;
    private static String capture;
    private static final List<String> images=new ArrayList<>();
    private static final List<String> cases=new ArrayList<>();
    private static float beforeCharge,peak;
    private static String prefix(){return "body-"+BodyStyle.values()[job/5+1].id()+"-"+Powertrain.values()[job%5].id();}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static CarEntity serverCar(ServerPlayer p){return (CarEntity)p.level().getEntity(carId);}
    private static void server(Minecraft mc,Consumer<ServerPlayer> task){
        var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            try{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);require(p!=null,"Lost native client");task.accept(p);}
            catch(Throwable e){e.printStackTrace();failure=e.toString();}
        });
    }
    private static void camera(Minecraft mc,double x,double feetY,double z,double targetY){
        server(mc,p->{var c=serverCar(p);p.teleportTo(c.getX()+x,origin.y+feetY,c.getZ()+z);p.getAbilities().flying=true;p.onUpdateAbilities();lookTarget=c.position().add(0,targetY,0);});
    }
    private static void action(Minecraft mc,int code){server(mc,p->{var c=serverCar(p);p.teleportTo(c.getX()+2.7,c.getY(),c.getZ());c.tickCount+=4;c.action(p,code,0,0);});}
    private static void shot(String view){require(capture==null,"Unflushed framebuffer capture");capture=prefix()+"-"+view+".png";}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("sparkmotors.clientBodySmoke")||done)return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(failure!=null){finish(mc,"FAIL "+failure);return;}
        if((System.nanoTime()-START)/1e9>900){finish(mc,"FAIL timeout job="+job+" ticks="+ticks);return;}
        try{
            if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
            if(!creating&&mc.screen instanceof TitleScreen){
                creating=true;mc.options.guiScale().set(2);mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);mc.options.hideGui=true;
                mc.createWorldOpenFlows().createFreshLevel("body-smoke-"+System.currentTimeMillis(),new LevelSettings("Body style verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(421,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),null);return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(job>=25){
                if(++finishTicks==30){require(capture==null,"Pending screenshot at finish");finish(mc,"PASS 25 body/powertrain cases; 5 native drive/brake cases; all opening panels, underbodies and plug-in charge ports captured; creative charging fixture explicitly used, not an ELN integration claim");}return;
            }
            if(!spawnPending&&carId<0){
                spawnPending=true;ticks=0;int current=job;
                server(mc,p->{
                    var level=p.serverLevel();var srv=level.getServer();
                    if(!prepared){
                        origin=new Vec3(p.blockPosition().getX()+.5,p.blockPosition().getY()+.05,p.blockPosition().getZ()+.5);
                        var base=BlockPos.containing(origin);level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,srv);level.setWeatherParameters(600000,0,false,false);
                        for(int x=-14;x<=14;x++)for(int z=-15;z<=45;z++)level.setBlock(base.offset(x,-1,z),(x== -3||x==3?Blocks.WHITE_CONCRETE:Blocks.SMOOTH_STONE).defaultBlockState(),3);prepared=true;
                    }
                    var type=Powertrain.values()[current%5];var style=BodyStyle.values()[current/5+1];
                    var c=AutoPropulsionAge.CAR.get().create(level);c.moveTo(origin.x,origin.y,origin.z,0,0);c.setOwner(p.getUUID());
                    c.setDriveConfig(DriveConfig.preset(DriveConfig.Layout.values()[current%3]));c.initializePowertrain(type,.35);c.initializeBodyStyle(style);
                    var tag=new CompoundTag();c.saveWithoutId(tag);tag.putInt("Paint",new int[]{0x29A6A6,0xCF383D,0x637A42,0xE6D8B9,0x324D80}[current/5]);c.load(tag);level.addFreshEntity(c);
                    p.teleportTo(c.getX()+3.5,origin.y+1.1,c.getZ()+5.5);p.getAbilities().flying=true;p.onUpdateAbilities();
                    lookTarget=c.position().add(0,style.roof()*.5,0);carId=c.getId();
                });return;
            }
            if(carId<0||!(mc.level.getEntity(carId) instanceof CarEntity car))return;ticks++;
            if(lookTarget!=null&&mc.options.getCameraType()==CameraType.FIRST_PERSON){
                var d=lookTarget.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
            }
            var body=BodyStyle.values()[job/5+1];var type=Powertrain.values()[job%5];
            if(ticks==25){
                require(car.bodyStyle()==body&&car.powertrain()==type,"Server/client body or powertrain mismatch "+prefix());
                require(car.mechanics().parts().size()>20,"Missing installed part topology "+prefix());
                require(car.getBoundingBox().getYsize()>=body.roof(),"Body query bounds smaller than rendered roof");
            }
            if(ticks==35)shot("front");
            if(ticks==45)camera(mc,3.6,1.0,-5.6,body.roof()*.5);
            if(ticks==70)shot("rear");
            if(ticks==80)action(mc,CarPackets.PANELS);
            if(ticks==90)camera(mc,3.4,1.25,5.1,.85);
            if(ticks==120){require(car.hoodOpen()&&car.hoodProgress>.95&&car.panelProgress>.95,"Panels not fully open "+prefix());shot("open");}
            if(ticks==130)action(mc,CarPackets.JACK);
            if(ticks==140)camera(mc,3.6,-.90,-4.3,.30);
            if(ticks==165){require(car.raised(),"Native body service jack failed");shot("underbody");}
            if(ticks==175){action(mc,CarPackets.JACK);}
            if(ticks==185){action(mc,CarPackets.PANELS);}
            if(ticks==195){
                camera(mc,3.4,1.0,5.3,body.roof()*.5);
                if(type.plugIn())server(mc,p->{
                    var c=serverCar(p);var level=p.serverLevel();chargerPos=BlockPos.containing(origin).offset(3,0,-2);
                    level.setBlock(chargerPos,Electrification.CHARGERS.get(ChargingModel.Tier.RAPID).get().defaultBlockState().setValue(ChargerBlock.FACING,Direction.SOUTH),3);
                    var charger=(ChargerBlockEntity)level.getBlockEntity(chargerPos);p.teleportTo(chargerPos.getX()+.5,chargerPos.getY(),chargerPos.getZ()+1);
                    p.setShiftKeyDown(true);charger.interact(p);p.setShiftKeyDown(false);
                    require(charger.connectCable(p,c,Electrification.CABLE.toStack()),"Charge pairing failed "+c.bodyStyle());
                });
            }
            if(ticks==210){beforeCharge=car.stateOfCharge();if(type.plugIn())camera(mc,4.6,.35,-4.6,body.chargeY());}
            if(ticks==250&&type.plugIn()){
                require(car.plugged()&&car.stateOfCharge()>beforeCharge,"Native charge energy did not synchronize "+prefix());shot("charging");
            }
            if(ticks==265)server(mc,p->{
                if(chargerPos!=null){var charger=(ChargerBlockEntity)p.level().getBlockEntity(chargerPos);if(charger!=null)charger.disconnect();p.level().removeBlock(chargerPos,false);chargerPos=null;}
                var c=serverCar(p);p.teleportTo(c.getX()+2.8,c.getY()+1.1,c.getZ()+3.5);
                if(type==Powertrain.COMBUSTION)CarPackets.open(p,c);
            });
            if(ticks==285&&type==Powertrain.COMBUSTION){
                require(mc.screen!=null&&!(mc.screen instanceof TitleScreen),"Garage preview not opened through native packet");mc.options.hideGui=false;shot("garage");
            }
            if(ticks==300){mc.setScreen(null);mc.options.hideGui=true;}
            if(type==Powertrain.ELECTRIC_400){
                if(ticks==310)server(mc,p->{p.getAbilities().flying=false;p.onUpdateAbilities();p.startRiding(serverCar(p),true);});
                if(ticks==325){require(mc.player.getVehicle()==car,"Native body mount did not synchronize");CarClient.send(car,CarPackets.IGNITION,0,0);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);lookTarget=null;peak=0;}
                if(ticks>335&&ticks<=460){mc.options.keyUp.setDown(ticks<395);mc.options.keyDown.setDown(ticks>=395);peak=Math.max(peak,car.horizontalSpeed());}
                if(ticks==385){mc.options.hideGui=false;shot("driving");}
                if(ticks==465){
                    mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);require(peak>3&&car.horizontalSpeed()<.3,"Native body drive/brake failed "+prefix()+" peak="+peak+" final="+car.horizontalSpeed());
                    require(car.fuel()==0&&car.rpm()==0,"BEV unexpectedly uses combustion");System.out.println("BODY_NATIVE_DRIVE_PASS "+body.id()+" peak_mps="+peak);next(mc);
                }
            }else if(ticks==310)next(mc);
        }catch(Throwable e){e.printStackTrace();finish(mc,"FAIL job="+job+" tick="+ticks+" "+e);}
    }
    private static void next(Minecraft mc){
        String completed=prefix();cases.add(completed);System.out.println("BODY_NATIVE_CASE_PASS "+completed);
        int previous=carId;carId=-1;spawnPending=true;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=true;
        server(mc,p->{p.stopRiding();var c=p.level().getEntity(previous);if(c!=null)c.discard();spawnPending=false;});job++;ticks=0;
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null||done)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->System.out.println("BODY_SCREENSHOT "+name));images.add(name);
    }
    private static void finish(Minecraft mc,String result){
        if(done)return;done=true;mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);System.out.println("BODY_NATIVE_RESULT "+result);
        try{
            Files.writeString(mc.gameDirectory.toPath().resolve("body-smoke-result.txt"),result+"\n");
            Files.writeString(mc.gameDirectory.toPath().resolve("body-smoke-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("schema",1,"result",result,"cases",cases,"screenshots",images,"elapsedSeconds",(System.nanoTime()-START)/1e9)));
        }catch(Exception e){throw new IllegalStateException(e);}mc.stop();
    }
}
