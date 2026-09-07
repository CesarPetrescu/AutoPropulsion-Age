package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.client.*;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.Assembly;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;

/** Development-only client integration harness. Never included in the release jar. */
@EventBusSubscriber(modid="sparkmotors",value=Dist.CLIENT)
public final class ClientSmoke {
    private static int phase,ticks,carId=-1;
    private static String pendingScreenshot;
    private static boolean creating;
    private static float maxSpeed;
    private static final long started=System.nanoTime();
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("sparkmotors.clientSmoke"))return;
        var mc=Minecraft.getInstance();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;
        mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
        if((System.nanoTime()-started)/1e9>240){write(mc,"FAILED: client smoke timed out at phase "+phase);mc.stop();return;}
        if(!creating&&mc.screen instanceof TitleScreen){
            creating=true;mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
            mc.createWorldOpenFlows().createFreshLevel("alpha-smoke-"+System.currentTimeMillis(),
                new LevelSettings("Alpha Smoke",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),null);
            return;
        }
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==0&&ticks>40){
            phase=1;ticks=0;
            var playerId=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayer(playerId);var level=p.serverLevel();
                level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                var car=AutoPropulsionAge.CAR.get().create(level);
                var origin=p.blockPosition();
                for(int dx=-10;dx<=10;dx++)for(int dz=-10;dz<=32;dz++)level.setBlock(origin.offset(dx,-1,dz),net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(),3);
                car.moveTo(p.getX(),p.getY()+.05,p.getZ()+5,0,0);car.setOwner(p.getUUID());level.addFreshEntity(car);carId=car.getId();
                p.teleportTo(p.getX()+5,p.getY()+1,p.getZ()-1);p.setYRot(-40);p.setXRot(12);
                p.getInventory().add(AutoPropulsionAge.FUEL_CAN.toStack());
                for(var a:Assembly.values())p.getInventory().add(new ItemStack(AutoPropulsionAge.partItem(a,2)));
                CarPackets.open(p,car);
            });
        }
        if(!(mc.level.getEntity(carId) instanceof CarEntity car))return;
        if(phase==4&&ticks==20)pendingScreenshot="alpha-paint.png";
        if(phase==6&&ticks==10)press(mc,"Car",0);
        if(phase==6&&ticks==20)pendingScreenshot="alpha-car-controls.png";
        if(phase==1&&ticks>65){pendingScreenshot="alpha-garage.png";phase=2;ticks=0;}
        else if(phase==2&&ticks>20){
            press(mc,"Sport",2);phase=3;ticks=0;
        }else if(phase==3&&ticks>25){
            if(Assembly.WHEELS.variant(car.config())!=2){write(mc,"FAILED: real network garage installation did not synchronize");mc.stop();return;}
            press(mc,"Paint",0);press(mc,"Crimson",0);phase=4;ticks=0;
        }else if(phase==4&&ticks>25){
            if(car.paint()!=0xBF333B){write(mc,"FAILED: paint did not synchronize");mc.stop();return;}
            press(mc,"Tuner",0);press(mc,"-",0);press(mc,"-",0);
            for(int i=0;i<4;i++)press(mc,"+",1);
            press(mc,"Apply tune",0);phase=5;ticks=0;
        }else if(phase==5&&ticks>25){
            if(car.limiter()!=6400||Math.abs(car.finalDrive()-4.1)>.01){write(mc,"FAILED: tune did not synchronize");mc.stop();return;}
            pendingScreenshot="alpha-tuner.png";CarClient.send(car,CarPackets.PANELS,0,0);phase=6;ticks=0;
        }else if(phase==6&&ticks>30){
            press(mc,"Garage",0);pendingScreenshot="alpha-customized.png";phase=7;ticks=0;
        }else if(phase==7&&ticks>15){
            mc.setScreen(null);CarClient.send(car,CarPackets.PANELS,0,0);
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);p.startRiding(mc.getSingleplayerServer().overworld().getEntity(carId),true);});
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);phase=8;ticks=0;
        }else if(phase==8&&ticks>25){CarClient.send(car,CarPackets.IGNITION,0,0);phase=9;ticks=0;}
        else if(phase==9){
            maxSpeed=Math.max(maxSpeed,Math.abs(car.speed()));
            PacketDistributor.sendToServer(new CarPackets.Input(carId,ticks<65?1:4,0));
            if(ticks==55)pendingScreenshot="alpha-driving.png";
            if(ticks==80)mc.options.setCameraType(CameraType.FIRST_PERSON);
            if(ticks==95)pendingScreenshot="alpha-interior.png";
            if(ticks>120){
                if(car.fuel()>=40||Math.abs(car.speed())>.3||maxSpeed<5){write(mc,"FAILED: driving/braking/fuel check; peak speed="+maxSpeed);mc.stop();return;}
                write(mc,"PASS: client world loaded; car rendered; garage installation, paint and tune synchronized over real packets; engine started, drove and braked; HUD and screenshots captured.");mc.stop();phase=10;
            }
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(pendingScreenshot!=null){var mc=Minecraft.getInstance();String name=pendingScreenshot;pendingScreenshot=null;
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->System.out.println("ALPHA_SCREENSHOT "+name));}
    }
    private static void write(Minecraft mc,String message){
        try{Files.writeString(mc.gameDirectory.toPath().resolve("alpha-smoke-result.txt"),message);}catch(Exception e){throw new RuntimeException(e);}
        System.out.println("ALPHA_CLIENT_SMOKE "+message);
    }
    private static void press(Minecraft mc,String label,int index){
        var buttons=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button)
            .map(c->(net.minecraft.client.gui.components.Button)c).filter(b->b.getMessage().getString().equals(label)).toList();
        if(index>=buttons.size()||!buttons.get(index).active)throw new IllegalStateException("Smoke button unavailable: "+label+" #"+index);
        buttons.get(index).onPress();
    }
}
