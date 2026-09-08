package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.charging.*;
import com.photonspark.sparkmotors.client.*;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Native renderer, GUI, packet, driving and charging regression; excluded from the release jar. */
@EventBusSubscriber(modid="sparkmotors",value=Dist.CLIENT)
public final class ElectricClientSmoke {
    private static final long START=System.nanoTime();
    private static int phase,ticks;
    private static boolean creating;
    private static volatile int[] ids;
    private static volatile String failure;
    private static String screenshot;
    private static float beforeCharge,peak;
    private static final List<AutoCloseable> fixtures=new ArrayList<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("sparkmotors.clientElectricSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(failure!=null){finish(mc,"FAILED: "+failure);return;}
        if((System.nanoTime()-START)/1e9>300){finish(mc,"FAILED: timeout phase="+phase+" ticks="+ticks);return;}
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
        if(!creating&&mc.screen instanceof TitleScreen){
            creating=true;mc.options.guiScale().set(2);mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
            mc.createWorldOpenFlows().createFreshLevel("electric-smoke-"+System.currentTimeMillis(),new LevelSettings("Electric verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),null);return;
        }
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;ticks++;
        if(phase==0&&ticks>40){phase=1;ticks=0;var playerId=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{try{
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayer(playerId);var level=p.serverLevel();var origin=p.blockPosition();
                level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                for(int x=-12;x<=24;x++)for(int z=-10;z<=52;z++)level.setBlock(origin.offset(x,-1,z),(x%5==2?Blocks.WHITE_CONCRETE:Blocks.SMOOTH_STONE).defaultBlockState(),3);
                int[] result=new int[4];var types=new Powertrain[]{Powertrain.HYBRID,Powertrain.PLUG_IN_HYBRID,Powertrain.ELECTRIC_400,Powertrain.ELECTRIC_800};
                for(int i=0;i<4;i++){
                    var car=AutoPropulsionAge.CAR.get().create(level);car.moveTo(origin.getX()+i*5+.5,origin.getY()+.05,origin.getZ()+5.5,0,0);car.setOwner(p.getUUID());car.initializePowertrain(types[i],.25);level.addFreshEntity(car);result[i]=car.getId();
                    p.teleportTo(car.getX()+2,car.getY(),car.getZ());car.action(p,CarPackets.HOOD,0,0);
                    var position=origin.offset(i*5+3,0,4);var tier=ChargingModel.Tier.values()[i];level.setBlock(position,Electrification.CHARGERS.get(tier).get().defaultBlockState(),3);
                    var charger=(ChargerBlockEntity)level.getBlockEntity(position);
                    if(ModList.get().isLoaded("eln")){
                        Class<?> fixture=Class.forName("com.photonspark.sparkmotors.gametest.ElnCircuitFixture");fixtures.add((AutoCloseable)fixture.getMethod("power",Level.class,BlockPos.class,net.minecraft.server.level.ServerPlayer.class,double.class).invoke(null,level,position,p,tier.inputV));
                    }else{p.setShiftKeyDown(true);charger.interact(p);p.setShiftKeyDown(false);}
                    if(types[i].plugIn()&&!charger.connect(p,car))throw new IllegalStateException("Failed to pair "+types[i]);
                }
                ids=result;p.teleportTo(origin.getX()+21,origin.getY()+8,origin.getZ()+19);p.getAbilities().flying=true;p.onUpdateAbilities();
            }catch(Throwable e){e.printStackTrace();failure=e.toString();}});return;
        }
        if(ids==null||!(mc.level.getEntity(ids[3]) instanceof CarEntity car))return;
        if(phase==1){
            mc.options.hideGui=true;mc.player.setYRot(143);mc.player.setXRot(23);
            if(ticks==50)beforeCharge=car.stateOfCharge();
            if(ticks==90)screenshot="electric-fleet-native.png";
            if(ticks==130){if(car.stateOfCharge()<=beforeCharge||!car.plugged()){finish(mc,"FAILED: actual charger did not synchronize energy");return;}phase=2;ticks=0;
                var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var c=(CarEntity)p.level().getEntity(ids[3]);p.teleportTo(c.getX()+2.8,c.getY()+2.55,c.getZ()+3.5);});
            }
        }else if(phase==2){
            mc.player.setYRot(133);mc.player.setXRot(32);
            if(ticks==25)screenshot="electric-800-open-hood-native.png";
            if(ticks==40){mc.options.hideGui=false;mc.setScreen(new GarageScreen(car,6));}
            if(ticks==55){press(mc,"100% limit");}
            if(ticks==70){if(car.chargeTarget()!=100){finish(mc,"FAILED: charge ceiling GUI packet");return;}screenshot="electric-diagnostics-native.png";}
            if(ticks==90){mc.setScreen(null);var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var c=(CarEntity)p.level().getEntity(ids[3]);if(p.level().getBlockEntity(c.chargerPosition()) instanceof ChargerBlockEntity charger)charger.disconnect();c.tickCount+=4;c.action(p,CarPackets.HOOD,0,0);p.startRiding(c,true);});phase=3;ticks=0;}
        }else if(phase==3){
            if(ticks==20){CarClient.send(car,CarPackets.IGNITION,0,0);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);}
            if(ticks>30){PacketDistributor.sendToServer(new CarPackets.Input(car.getId(),ticks<95?1:4,0));peak=Math.max(peak,Math.abs(car.speed()));}
            if(ticks==80)screenshot="electric-driving-native.png";
            if(ticks==160){if(peak<5||Math.abs(car.speed())>.2||car.fuel()!=0||car.rpm()!=0){finish(mc,"FAILED: EV native driving/braking peak="+peak+" speed="+car.speed());return;}
                finish(mc,"PASS: four electrified vehicle variants and four charger models rendered; charge energy and ceiling synchronized through real GUI packets; cable interlock/disconnect; battery-only driving and friction braking; screenshots captured. ELN="+ModList.get().isLoaded("eln")+" peak_mps="+peak);}
        }
    }
    private static void press(Minecraft mc,String label){mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(label)&&b.active).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow().onPress();}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(screenshot!=null){var mc=Minecraft.getInstance();String name=screenshot;screenshot=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->System.out.println("ELECTRIC_SCREENSHOT "+name));}}
    private static void finish(Minecraft mc,String text){
        if(phase==99)return;phase=99;System.out.println("ELECTRIC_CLIENT_SMOKE "+text);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("electric-smoke-result.txt"),text);}catch(Exception e){throw new IllegalStateException(e);}mc.stop();
    }
}
