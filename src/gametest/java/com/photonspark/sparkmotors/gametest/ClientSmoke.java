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
    private static int engineJob,hardwareJob;
    private static final String[] MODES={"natural","turbo","supercharger","large-turbo","twin-turbo","roots","twin-screw"};
    private static final long started=System.nanoTime();
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("sparkmotors.clientSmoke"))return;
        var mc=Minecraft.getInstance();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;
        mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
        if((System.nanoTime()-started)/1e9>1000){write(mc,"FAILED: client smoke timed out at phase "+phase+", engine layout "+engineJob);mc.stop();return;}
        if(Boolean.getBoolean("sparkmotors.clientUi")&&!WorkshopClient.branding(mc))return;
        if(!creating&&mc.screen instanceof TitleScreen){
            creating=true;mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
            if(Boolean.getBoolean("sparkmotors.clientGraphics"))GraphicsClient.beforeWorld(mc);
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
                int pad=Boolean.getBoolean("sparkmotors.clientHandling")?80:10;
                for(int dx=-pad;dx<=pad;dx++)for(int dz=-pad;dz<=Math.max(32,pad);dz++)level.setBlock(origin.offset(dx,-1,dz),net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(),3);
                car.moveTo(p.getX(),p.getY()+.05,p.getZ()+5,0,0);car.setOwner(p.getUUID());level.addFreshEntity(car);carId=car.getId();
                p.teleportTo(p.getX()+5,p.getY()+1,p.getZ()-1);p.setYRot(-40);p.setXRot(12);
                p.getInventory().add(AutoPropulsionAge.FUEL_CAN.toStack());
                for(var a:Assembly.values())p.getInventory().add(new ItemStack(AutoPropulsionAge.partItem(a,2)));
                CarPackets.open(p,car);
            });
        }
        if(!(mc.level.getEntity(carId) instanceof CarEntity car))return;
        if(Boolean.getBoolean("sparkmotors.clientGraphics")){GraphicsClient.tick(mc,car);return;}
        if(Boolean.getBoolean("sparkmotors.clientUi")){WorkshopClient.tick(mc,car);return;}
        if(Boolean.getBoolean("sparkmotors.clientHandling")){HandlingClient.tick(mc,car);return;}
        if(phase>=10){if(Boolean.getBoolean("sparkmotors.clientMechanics"))componentFlow(mc,car);else engineMatrix(mc,car);return;}
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
            PacketDistributor.sendToServer(new CarPackets.Input(carId,ticks<65?1:2,0));
            if(ticks==55){if(CarClient.activeAudioVoices()==0)throw new IllegalStateException("No actual engine/road sound channels active");pendingScreenshot="alpha-driving.png";}
            if(ticks==80)mc.options.setCameraType(CameraType.FIRST_PERSON);
            if(ticks==90){mc.player.setXRot(27);mc.player.xRotO=27;mc.options.hideGui=true;}
            if(ticks==95)pendingScreenshot="mechanics-cockpit.png";
            if(ticks==102){mc.options.hideGui=false;mc.player.setXRot(5);}
            if(ticks>120){
                if(car.fuel()>=40||Math.abs(car.speed())>.3||maxSpeed<5){write(mc,"FAILED: driving/braking/fuel check; peak speed="+maxSpeed);mc.stop();return;}
                CarClient.send(car,CarPackets.IGNITION,0,0);phase=10;ticks=0;
            }
        }
    }
    private static void componentFlow(Minecraft mc,CarEntity car){
        if(phase==10){
            if(ticks==10){var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var c=(CarEntity)p.serverLevel().getEntity(carId);p.stopRiding();p.teleportTo(c.getX()+2.8,c.getY(),c.getZ());c.setMechanics(CircuitPhysics.impact(c.mechanics(),"front",18).fluids(2.4,5,1));});}
            if(ticks==15){var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var c=(CarEntity)mc.getSingleplayerServer().overworld().getEntity(carId);c.setMechanics(c.mechanics().with("engine.internals",c.mechanics().get("engine.internals").condition(.35,0,0)));});}
            if(ticks==30){CarClient.send(car,CarPackets.OPEN,0,0);}
            if(ticks==45){press(mc,"Service",0);}
            if(ticks==55){if(!car.hoodOpen())press(mc,"Hood",0);}
            if(ticks==75){pendingScreenshot="mechanics-leaking-hose.png";press(mc,"Tests / fluids",0);}
            if(ticks==85)press(mc,"Pressure test / 10s",0);
            if(ticks==300){if(!car.diagnostic().contains("Pressure loss"))throw new IllegalStateException("Timed leak test did not synchronize: "+car.diagnostic());pendingScreenshot="mechanics-pressure-loss.png";}
            if(ticks==320){press(mc,"Parts",0);press(mc,"Install part",0);}
            if(ticks==340){if(CircuitPhysics.coolantLeak(car.mechanics())>.005)throw new IllegalStateException("Hose replacement did not stop the leak");if(car.coolant()>2.5)throw new IllegalStateException("Part replacement secretly refilled coolant");press(mc,"Tests / fluids",0);}
            if(ticks>=355&&ticks<=385&&ticks%5==0)press(mc,"Fill coolant",0);
            if(ticks==400){if(car.coolant()<7.999)throw new IllegalStateException("Full coolant refill packet sequence failed");press(mc,"Pressure test / 10s",0);}
            if(ticks==615){if(!car.diagnostic().contains("Holds pressure"))throw new IllegalStateException("Repaired circuit failed pressure verification");pendingScreenshot="mechanics-repair-verified.png";}
            if(ticks==620){if(car.mechanics().get("engine.internals").wear()<.34)throw new IllegalStateException("Hose replacement healed unrelated internal wear");press(mc,"Garage",0);press(mc,"Live",0);}
            if(ticks==625)press(mc,"Rebuild engine",0);
            if(ticks==630){if(car.mechanics().get("engine.internals").wear()!=0)throw new IllegalStateException("Native rebuild button failed to repair wear-only internals");press(mc,"Service",0);}
            if(ticks==635){press(mc,"Parts",0);((ServiceScreen)mc.screen).select("wheel.fl.tire");press(mc,"Jack",0);}
            if(ticks==655){if(!car.raised())throw new IllegalStateException("Jack did not synchronize");press(mc,"Remove part",0);}
            if(ticks==675){if(car.mechanics().get("wheel.fl.tire")!=null||CarMesh.visibleComponentTriangles(car,"wheel.fl.tire")!=0)throw new IllegalStateException("Removed tire remains installed or visible");press(mc,"Underside",0);pendingScreenshot="mechanics-underside-service.png";}
            if(ticks==695)press(mc,"Install part",0);
            if(ticks==715){if(car.mechanics().get("wheel.fl.tire")==null||CarMesh.visibleComponentTriangles(car,"wheel.fl.tire")==0)throw new IllegalStateException("Fitted tire did not reappear");((ServiceScreen)mc.screen).select("cooling.upper_hose");press(mc,"Focus part",0);}
            if(ticks==730){if(CarMesh.visibleComponentTriangles(car,"cooling.upper_hose")==0||CarMesh.visibleComponentTriangles(car,"cooling.lower_hose")==0)throw new IllegalStateException("Independent coolant hose geometry missing");pendingScreenshot="mechanics-focused-hose.png";}
            if(ticks==745){var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var c=(CarEntity)p.serverLevel().getEntity(carId);c.setMechanics(c.mechanics().with("cooling.sender",null));});}
            if(ticks==770){if(!Double.isNaN(CockpitInstruments.read(car).coolant()))throw new IllegalStateException("Failed coolant sender still supplies a perfect cockpit reading");((ServiceScreen)mc.screen).select("cooling.sender");press(mc,"Install part",0);}
            if(ticks==780){press(mc,"Jack",0);}
            if(ticks==790){press(mc,"Hood",0);mc.setScreen(null);maxSpeed=0;var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);p.startRiding(p.serverLevel().getEntity(carId),true);});mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);}
            if(ticks==810)CarClient.send(car,CarPackets.IGNITION,0,0);
            if(ticks>=820&&ticks<=960){PacketDistributor.sendToServer(new CarPackets.Input(carId,ticks<900?1:2,0));maxSpeed=Math.max(maxSpeed,Math.abs(car.speed()));}
            if(ticks==890)pendingScreenshot="mechanics-repaired-driving.png";
            if(ticks==970){if(maxSpeed<5||Math.abs(car.speed())>.3||!car.engineRunning()||car.coolant()<7.99||car.temperature()>110)throw new IllegalStateException("Repaired car failed running/road verification: speed="+maxSpeed+" coolant="+car.coolant());
                write(mc,"PASS: mechanical workshop native GUI/network leak diagnosis, targeted hose replacement, full conserved refill, timed verification, physical jack, tire visibility, focused separate hoses, failed sender behavior and repaired-car driving.");
                CarClient.stopSounds();if(CarClient.activeAudioVoices()!=0)throw new IllegalStateException("Audio voices survived cleanup");System.out.println("MECHANICS_CLIENT_PASS AUDIO_CHANNELS_AND_CLEANUP_PASS INSTRUMENT_SENDER_PASS");mc.stop();}
        }
    }
    private static void engineMatrix(Minecraft mc,CarEntity car){
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
        if(phase>=14){hardwareMatrix(mc,car);return;}
        EngineFamily wanted=EngineFamily.values()[engineJob/7];int induction=engineJob%7;
        if(phase==11&&ticks>=15){
            if(car.engineFamily()!=wanted){press(mc,">",0);press(mc,"Fit stock",0);}
            phase=12;ticks=0;
        }else if(phase==12){
            int[] slots={0,1,2,3,4,6,7,8,9,5},choices={2,3,2,3,4,3,2,2,3,induction};
            if(ticks>=8&&ticks<=80&&ticks%8==0){int index=ticks/8-1;choosePart(mc,car,EnginePart.values()[slots[index]],choices[index]);}
            if(ticks==94){phase=13;ticks=0;}
        }else if(phase==13){
            String label=wanted.id+"-"+MODES[induction];
            if(ticks==20){
                var visible=CarMesh.visibleEngineParts(car);
                if(car.engineFamily()!=wanted||EnginePart.INDUCTION.variant(car.engineParts())!=induction||!car.engineProblem().isEmpty()||CarMesh.visibleEngineFamilies(car)!=(1<<wanted.ordinal())||
                    !exclusiveCompressor(visible,induction)){
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
                if(engineJob==(Boolean.getBoolean("sparkmotors.clientQuick")?7:49)){CarClient.send(car,CarPackets.OPEN_ENGINE,0,0);phase=14;ticks=0;}
                else{CarClient.send(car,CarPackets.OPEN_ENGINE,0,0);phase=11;ticks=0;}
            }
        }
    }
    private static boolean exclusiveCompressor(java.util.Map<String,Integer> visible,int mode){
        String[] names={"turbocharger","supercharger","large_turbo","twin_turbo","roots_blower","twin_screw"};
        for(int i=0;i<names.length;i++)if(visible.containsKey(names[i])!=(mode==i+1))return false;return true;
    }
    private static void choosePart(Minecraft mc,CarEntity car,EnginePart slot,int value){
        if(slot.variant(car.engineParts())==value)return;
        if(!(mc.screen instanceof GarageScreen))throw new IllegalStateException("Engine workshop is not open");
        // Drive the same arrow and Fit widgets as a player. Scroll only changes which rows are visible.
        for(int i=0;i<10;i++)mc.screen.mouseScrolled(mc.screen.width-30,150,0,1);
        int rows=Math.max(1,Math.min(10,(Math.min(430,mc.screen.height-16)-232)/40));
        int scroll=Math.min(slot.ordinal(),10-rows);
        for(int i=0;i<scroll;i++)mc.screen.mouseScrolled(mc.screen.width-30,150,0,-1);
        int clicks=(value-slot.variant(car.engineParts())+slot.maxVariant()+1)%(slot.maxVariant()+1);
        for(int i=0;i<clicks;i++)press(mc,">",1+slot.ordinal()-scroll);
        String label=value==0?(slot==EnginePart.INDUCTION?"Naturally aspirated":"Remove"):"Fit: "+slot.label(value);
        var matches=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button).map(c->(net.minecraft.client.gui.components.Button)c).filter(b->label.startsWith(b.getMessage().getString())&&b.active).toList();
        if(matches.isEmpty())throw new IllegalStateException("No enabled fit button for "+slot+"="+value);matches.getFirst().onPress();
    }
    private static void hardwareMatrix(Minecraft mc,CarEntity car){
        if(phase==14){
            if(ticks==15)choosePart(mc,car,EnginePart.INDUCTION,0);
            if(ticks==30){phase=15;ticks=0;}return;
        }
        if(phase==15){
            int index=hardwareJob;EnginePart slot=null;int choice=0;
            for(var p:EnginePart.values()){if(index<p.maxVariant()){slot=p;choice=index+1;break;}index-=p.maxVariant();}
            if(slot==null){phase=16;ticks=0;return;}
            if(ticks==8)choosePart(mc,car,slot,choice);
            if(ticks==20){
                if(slot.variant(car.engineParts())!=choice)throw new IllegalStateException("Hardware did not synchronize: "+slot+choice);
                pendingScreenshot="hardware-ui-"+slot.itemName(choice)+".png";System.out.println("HARDWARE_UI_PASS "+slot.itemName(choice));
            }
            if(ticks==25){
                // Reset on each category boundary so the next choice has a compatible road baseline.
                if(choice==slot.maxVariant()){phase=17;ticks=0;}else{hardwareJob++;ticks=0;}
            }return;
        }
        if(phase==17){
            int[] slots={5,1,4,2,3},choices={0,3,4,2,3};
            if(ticks>=8&&ticks<=40&&ticks%8==0)choosePart(mc,car,EnginePart.values()[slots[ticks/8-1]],choices[ticks/8-1]);
            if(ticks==52){hardwareJob++;phase=15;ticks=0;}return;
        }
        if(phase==16){
            if(ticks==8)press(mc,"Live",0);
            if(ticks==16)press(mc,"Start / stop",0);
            if(ticks==45){if(!car.engineRunning())throw new IllegalStateException("Engine did not finish cranking before parked rev test: "+car.engineMode());press(mc,"Rev test / 2s",0);}
            if(ticks==60){if(car.rpm()<1500||Math.abs(car.speed())>.1)throw new IllegalStateException("Parked rev test failed");pendingScreenshot="powertrain-live.png";}
            if(ticks==80)press(mc,"Start / stop",0);
            if(ticks==95){press(mc,"Tuner",0);press(mc,"-",2);press(mc,"Apply boost",0);}
            if(ticks==110){if(Math.abs(car.boostTarget()-1.3)>.01)throw new IllegalStateException("Boost tune failed over the real packet path");pendingScreenshot="powertrain-tuner.png";}
            if(ticks==125){write(mc,"PASS: driving/garage/paint/tune plus "+engineJob+" family/induction layouts and all 42 hardware choices installed through native GUI buttons and real packets. Exclusive compressors, live rev test, diagnostics, boost tuning and screenshots verified.");mc.stop();phase=18;}
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(Boolean.getBoolean("sparkmotors.clientGraphics"))GraphicsClient.frame(Minecraft.getInstance());
        if(pendingScreenshot!=null){var mc=Minecraft.getInstance();String name=pendingScreenshot;pendingScreenshot=null;
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->System.out.println("ALPHA_SCREENSHOT "+name));}
    }
    @SubscribeEvent public static void worldFrame(RenderLevelStageEvent e){
        if(Boolean.getBoolean("sparkmotors.clientGraphics"))GraphicsClient.rendered(e);
    }
    static void screenshot(String name){pendingScreenshot=name;}
    static boolean screenshotPending(){return pendingScreenshot!=null;}
    static void write(Minecraft mc,String message){
        try{Files.writeString(mc.gameDirectory.toPath().resolve("alpha-smoke-result.txt"),message);}catch(Exception e){throw new RuntimeException(e);}
        System.out.println("ALPHA_CLIENT_SMOKE "+message);
    }
    static void press(Minecraft mc,String label,int index){
        var buttons=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button)
            .map(c->(net.minecraft.client.gui.components.Button)c).filter(b->b.getMessage().getString().equals(label)).toList();
        if(index>=buttons.size()||!buttons.get(index).active)throw new IllegalStateException("Smoke button unavailable: "+label+" #"+index);
        buttons.get(index).onPress();
    }
}
