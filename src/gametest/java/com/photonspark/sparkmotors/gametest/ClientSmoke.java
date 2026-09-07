package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.client.*;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
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
    private static int engineJob;
    private static final long started=System.nanoTime();
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("sparkmotors.clientSmoke"))return;
        var mc=Minecraft.getInstance();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;
        mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
        if((System.nanoTime()-started)/1e9>600){write(mc,"FAILED: client smoke timed out at phase "+phase+", engine layout "+engineJob);mc.stop();return;}
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
        if(phase>=10){engineMatrix(mc,car);return;}
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
                CarClient.send(car,CarPackets.IGNITION,0,0);phase=10;ticks=0;
            }
        }
    }
    private static void engineMatrix(Minecraft mc,CarEntity car){
        if(phase>=15)return;
        if(phase==14){
            if(ticks==1)mc.getSingleplayerServer().execute(()->{
                var c=(CarEntity)mc.getSingleplayerServer().overworld().getEntity(carId);
                c.condition().damage(VehicleCondition.Part.LEFT_BODY,70);c.condition().damage(VehicleCondition.Part.ENGINE_BLOCK,65);
                c.condition().damage(VehicleCondition.Part.TIRE_FL,80);c.condition().damage(VehicleCondition.Part.COOLING,60);c.syncCondition();
            });
            if(ticks==10)CarClient.send(car,CarPackets.OPEN,0,0);
            if(ticks==25)press(mc,"Inspect",0);
            if(ticks==40)pendingScreenshot="damage-inspection.png";
            if(ticks==45)press(mc,"Cutaway",0);
            if(ticks==55)pendingScreenshot="damage-cutaway.png";
            if(ticks==60)press(mc,"Repair",3);
            if(ticks==80){
                if(car.condition().health(VehicleCondition.Part.LEFT_BODY)<99.99||car.condition().health(VehicleCondition.Part.ENGINE_BLOCK)>36){write(mc,"FAILED: selective repair did not synchronize");mc.stop();return;}
                pendingScreenshot="damage-selective-repair.png";
            }
            if(ticks==95){write(mc,"PASS: driving, garage, all 21 family/induction layouts and active audio sources, damaged component synchronization, native inspection/cutaway, and selective repair checked. Screenshots captured.");mc.stop();phase=15;}
            return;
        }
        if(phase==10){
            if(ticks==10)CarClient.send(car,CarPackets.HOOD,0,0);
            if(ticks==20){
                var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var c=mc.getSingleplayerServer().overworld().getEntity(carId);
                    p.stopRiding();p.teleportTo(c.getX()+2.8,c.getY()+2.55,c.getZ()+3.5);p.getAbilities().flying=true;p.onUpdateAbilities();
                });
            }
            if(ticks==45){mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.getInventory().selected=8;mc.options.hideGui=true;mc.player.setYRot(133);mc.player.setXRot(32);pendingScreenshot="engine-open-hood.png";}
            if(ticks==60){mc.options.hideGui=false;CarClient.send(car,CarPackets.OPEN_ENGINE,0,0);phase=11;ticks=0;}
            return;
        }
        EngineFamily wanted=EngineFamily.values()[engineJob/3];int induction=engineJob%3;
        if(phase==11&&ticks>=15){
            if(car.engineFamily()!=wanted){press(mc,">",0);press(mc,"Fit stock",0);}
            phase=12;ticks=0;
        }else if(phase==12){
            if(ticks==15&&EnginePart.FUEL.variant(car.engineParts())!=2)press(mc,"Upgrade",1);
            if(ticks==25&&EnginePart.INTERNALS.variant(car.engineParts())!=2)press(mc,"Upgrade",4);
            if(ticks==35&&EnginePart.COOLING.variant(car.engineParts())!=2)press(mc,"Upgrade",3);
            if(ticks==45&&EnginePart.INTAKE.variant(car.engineParts())!=2)press(mc,"Upgrade",0);
            if(ticks==55&&EnginePart.IGNITION.variant(car.engineParts())!=2)press(mc,"Upgrade",2);
            if(ticks==70&&EnginePart.INDUCTION.variant(car.engineParts())!=induction)press(mc,new String[]{"Natural","Turbo","Blower"}[induction],0);
            if(ticks==90){phase=13;ticks=0;}
        }else if(phase==13){
            if(ticks==1)CarClient.send(car,CarPackets.IGNITION,0,0);
            if(ticks==55)CarClient.send(car,CarPackets.IGNITION,0,0);
            String label=wanted.id+"-"+new String[]{"natural","turbo","supercharger"}[induction];
            if(ticks==20){
                var visible=CarMesh.visibleEngineParts(car);
                if(!car.ignition()||CarAudioController.activeSourceCount(car.getId())==0){write(mc,"FAILED: engine audio source did not start for "+label);mc.stop();return;}
                if(car.engineFamily()!=wanted||EnginePart.INDUCTION.variant(car.engineParts())!=induction||!car.engineProblem().isEmpty()||CarMesh.visibleEngineFamilies(car)!=(1<<wanted.ordinal())||
                    visible.containsKey("turbocharger")!=(induction==1)||visible.containsKey("supercharger")!=(induction==2)){
                    write(mc,"FAILED: client engine configuration or renderer visibility mismatch: "+label);mc.stop();return;
                }
                System.out.println("ENGINE_LAYOUT_PASS "+label+" parts="+car.engineParts()+" triangles="+visible.values().stream().mapToInt(Integer::intValue).sum());
                pendingScreenshot="engine-"+label+".png";
            }
            if(induction==0&&ticks==30)press(mc,"Inspect internals",0);
            if(induction==0&&ticks==40)pendingScreenshot="engine-"+wanted.id+"-internals.png";
            if(induction==0&&ticks==50)press(mc,"Show covers",0);
            if(ticks==60){mc.setScreen(null);mc.options.hideGui=true;mc.player.setYRot(133);mc.player.setXRot(32);}
            if(ticks==70)pendingScreenshot="hood-"+label+".png";
            if(ticks==85){
                mc.options.hideGui=false;
                engineJob++;
                if(engineJob==21){phase=14;ticks=0;}
                else{CarClient.send(car,CarPackets.OPEN_ENGINE,0,0);phase=11;ticks=0;}
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
