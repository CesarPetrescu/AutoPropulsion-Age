package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.BodyStyle;
import com.photonspark.sparkmotors.sim.electric.Powertrain;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Close native views of actual shells with their production culling/material path. */
@EventBusSubscriber(modid="sparkmotors",value=Dist.CLIENT)
public final class ShellSeamClientSmoke {
    private record View(String name,Vec3 eye,Vec3 target,boolean open,boolean raised,boolean night) {}
    private static final long START=System.nanoTime();
    private static final List<String> images=new ArrayList<>();
    private static final List<Map<String,Object>> cases=new ArrayList<>();
    private static boolean creating,done;
    private static volatile boolean pending;
    private static volatile int carId=-1;
    private static volatile String failure;
    private static volatile Vec3 target;
    private static volatile BlockPos stage;
    private static int job,viewIndex,ticks,settle;
    private static String capture;
    private static final Powertrain[] TYPES={Powertrain.COMBUSTION,Powertrain.ELECTRIC_800};
    private static List<View> views(BodyStyle b){
        double h=b.roof(),belt=b.belt(),tail=b.tail(),w=b.halfWidth();
        return List.of(
            v("closed-front-left",-3.1,1.65,4.6,0,.9,.1,false,false,false),
            v("closed-front-right",3.1,1.65,4.6,0,.9,.1,false,false,false),
            v("closed-rear-left",-3.0,1.6,-4.5,0,1.0,-.4,false,false,false),
            v("closed-rear-right",3.0,1.6,-4.5,0,1.0,-.4,false,false,false),
            v("left-seams",-w-1.25,belt+.25,-.45,-w+.04,belt+.16,-.3,false,false,false),
            v("right-seams",w+1.25,belt+.25,-.45,w-.04,belt+.16,-.3,false,false,false),
            v("inside-forward",0,Math.min(h-.10,belt+.27),-.62,0,belt+.19,.9,false,false,false),
            v("inside-rear",0,Math.min(h-.12,belt+.28),.02,0,belt+.15,-tail,false,false,false),
            v("inside-roof",0,h-.22,-.45,.50,h,-.45,false,false,false),
            v("floor-joints",.03,Math.max(.98,belt-.02),-.25,.30,.43,-.85,false,false,false),
            v("rear-roof-join",-w-.75,h+.13,-tail-1.0,-w+.17,h-.15,-tail+.20,false,false,false),
            v("open-front",-2.8,1.65,4.3,0,.95,.6,true,false,false),
            v("open-rear-left",-3.0,1.6,-4.6,0,1.05,-.9,true,false,false),
            v("open-rear-right",3.0,1.6,-4.6,0,1.05,-.9,true,false,false),
            v("open-interior",0,Math.min(h-.12,belt+.25),-.4,-w,belt+.08,-.3,true,false,false),
            v("underbody",-2.8,.03,-3.7,0,.34,-.4,true,true,false),
            v("night-rear",-3.0,1.5,-4.5,0,1,-.5,false,false,true));
    }
    private static View v(String n,double x,double y,double z,double tx,double ty,double tz,boolean o,boolean r,boolean night){return new View(n,new Vec3(x,y,z),new Vec3(tx,ty,tz),o,r,night);}
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
    private static void server(Minecraft mc,Consumer<ServerPlayer> operation){
        var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);require(p!=null,"Native player disconnected");operation.accept(p);
        }catch(Throwable e){e.printStackTrace();failure=e.toString();pending=false;}});
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("sparkmotors.clientShellSmoke")||done)return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        try{
            if(failure!=null)throw new IllegalStateException(failure);
            require((System.nanoTime()-START)/1e9<1200,"Shell capture timeout");
            if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
            if(!creating&&mc.screen instanceof TitleScreen){
                creating=true;mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);mc.options.framerateLimit().set(60);mc.options.hideGui=true;
                mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
                mc.createWorldOpenFlows().createFreshLevel("shell-seams-"+System.currentTimeMillis(),new LevelSettings("Shell seam verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(421,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),null);return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(job>=12){if(++settle>30)finish(mc,"PASS 12 body/powertrain cases; 204 close native screenshots; captures require content review");return;}
            var body=BodyStyle.values()[job/2];var type=TYPES[job%2];
            if(carId<0&&!pending){
                pending=true;server(mc,p->{
                    var level=p.serverLevel();if(stage==null)stage=p.blockPosition();var base=stage;
                    for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)level.setBlock(base.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.setWeatherParameters(600000,0,false,false);
                    var c=AutoPropulsionAge.CAR.get().create(level);c.moveTo(base.getX()+.5,base.getY()+.05,base.getZ()+.5,0,0);c.initializePowertrain(type,.45);c.initializeBodyStyle(body);c.setOwner(p.getUUID());level.addFreshEntity(c);
                    p.getInventory().add(new ItemStack(AutoPropulsionAge.PART_ITEMS.get("service_jack").get()));
                    p.setGameMode(GameType.SPECTATOR);carId=c.getId();pending=false;
                });return;
            }
            if(pending||!(mc.level.getEntity(carId) instanceof CarEntity car))return;
            require(car.bodyStyle()==body&&car.powertrain()==type,"Body identity mismatch");
            if(viewIndex>=views(body).size()){
                require(capture==null,"Pending final frame");int old=carId;pending=true;
                cases.add(Map.of("body",body.id(),"powertrain",type.id(),"views",viewIndex));
                server(mc,p->{var c=p.level().getEntity(old);if(c!=null)c.discard();carId=-1;pending=false;});
                job++;viewIndex=0;ticks=0;return;
            }
            View view=views(body).get(viewIndex);
            if(ticks==0){
                pending=true;server(mc,p->{
                    var c=(CarEntity)p.level().getEntity(carId);require(c!=null,"Missing capture vehicle");
                    p.teleportTo(c.getX()+2.8,c.getY(),c.getZ());
                    if(c.raised()!=view.raised){c.tickCount+=4;c.action(p,CarPackets.JACK,0,0);}
                    if(c.hoodOpen()!=view.open){c.tickCount+=4;c.action(p,CarPackets.PANELS,0,0);}
                    p.serverLevel().setDayTime(view.night?18000:6000);
                    if(c.lights()!=view.night){c.tickCount+=4;c.action(p,CarPackets.LIGHTS,0,0);}
                    p.teleportTo(c.getX()+view.eye.x,c.getY()+view.eye.y-p.getEyeHeight(),c.getZ()+view.eye.z);
                    target=c.position().add(view.target);pending=false;
                });ticks=1;return;
            }
            ticks++;
            if(target!=null){var d=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
            if(ticks==22){
                require(car.raised()==view.raised,"Jack state not synchronized");
                require(view.open?car.panelProgress>.98&&car.hoodProgress>.98:car.panelProgress<.02&&car.hoodProgress<.02,"Panel state did not settle");
                require(capture==null,"Previous screenshot was not captured");capture="seams-"+body.id()+"-"+type.id()+"-"+view.name+".png";
            }
            if(ticks>=26&&capture==null){viewIndex++;ticks=0;}
        }catch(Throwable e){e.printStackTrace();finish(mc,"FAIL case="+job+" view="+viewIndex+" "+e);}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null||done)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->System.out.println("SHELL_SCREENSHOT "+name));images.add(name);
    }
    private static void finish(Minecraft mc,String result){
        if(done)return;done=true;System.out.println("SHELL_NATIVE_RESULT "+result);
        try{
            Files.writeString(mc.gameDirectory.toPath().resolve("shell-smoke-result.txt"),result+"\n");
            Files.writeString(mc.gameDirectory.toPath().resolve("shell-smoke-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("result",result,"cases",cases,"screenshots",images,"seconds",(System.nanoTime()-START)/1e9)));
        }catch(Exception e){throw new IllegalStateException(e);}mc.stop();
    }
}
