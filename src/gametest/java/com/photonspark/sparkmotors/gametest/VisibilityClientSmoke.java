package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.charging.*;
import com.photonspark.sparkmotors.client.CarMesh;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Native game-renderer regression. A tiny lime block-display is a visibility witness, not a
 * replacement for the car mesh. It is rendered by vanilla as another real entity in the cabin
 * (and outside the windshield), so opaque depth ordering can be asserted from framebuffer pixels.
 * Normal non-probe views are retained as separate screenshots for visual inspection.
 */
@EventBusSubscriber(modid="sparkmotors",value=Dist.CLIENT)
public final class VisibilityClientSmoke {
    private record View(String name,Vec3 eye,Vec3 target,boolean probe){}
    private static final long START=System.nanoTime();
    private static final List<String> failures=new ArrayList<>();
    private static final List<Map<String,Object>> captures=new ArrayList<>();
    private static final Powertrain[] TYPES={Powertrain.COMBUSTION,Powertrain.PLUG_IN_HYBRID,Powertrain.ELECTRIC_800};
    private static boolean creating,done;
    private static volatile boolean pending;
    private static volatile String serverError;
    private static volatile int carId=-1;
    private static volatile Vec3 target;
    private static volatile BlockPos stage;
    private static int job,view,ticks,frames,finishing;
    private static String capture;
    private static boolean witness;
    private static Comparison comparison;
    private static int comparisonWait;
    private record Comparison(String name,int limePixels,int[] pixels,BodyStyle body,List<?> original,Map<BodyStyle,List<?>> bodies){}

    /** Test-only paired render: ensure a passing witness isn't caused by missing glass entirely.
     * The production renderer is not given an override or a diagnostic switch. Restore the exact
     * resource chunk list immediately after the control capture, including on a failure.
     */
    @SuppressWarnings("unchecked")
    private static Comparison hideGlassForControl(String name,int lime,int[] pixels,BodyStyle body) throws ReflectiveOperationException {
        var field=CarMesh.class.getDeclaredField("bodyChunks");field.setAccessible(true);
        var bodies=(Map<BodyStyle,List<?>>)field.get(null);var original=bodies.get(body);
        require(original!=null,"Body resource missing for paired control");
        var opaque=new ArrayList<Object>();int glass=0;
        for(var chunk:original){
            var kind=chunk.getClass().getDeclaredMethod("kind");kind.setAccessible(true);
            if((int)kind.invoke(chunk)==2)glass++;else opaque.add(chunk);
        }
        require(glass>0,"No glass in the resource; visibility probe would be vacuous");
        bodies.put(body,List.copyOf(opaque));clearVisibleCache();
        return new Comparison(name,lime,pixels,body,original,bodies);
    }
    private static void clearVisibleCache() throws ReflectiveOperationException {
        var field=CarMesh.class.getDeclaredField("visibleCars");field.setAccessible(true);((Map<?,?>)field.get(null)).clear();
    }
    private static int[] region(com.mojang.blaze3d.platform.NativeImage image){
        var result=new int[180*180];int i=0;
        for(int y=image.getHeight()/2-90;y<image.getHeight()/2+90;y++)for(int x=image.getWidth()/2-90;x<image.getWidth()/2+90;x++)result[i++]=image.getPixelRGBA(x,y);
        return result;
    }
    private static boolean lime(int abgr){int r=abgr&255,g=abgr>>8&255,b=abgr>>16&255;return g>55&&g>r*1.3&&g>b*1.5;}
    private static int limeCount(int[] pixels){int result=0;for(int pixel:pixels)if(lime(pixel))result++;return result;}
    private static void restoreGlass(){
        var old=comparison;comparison=null;if(old==null)return;
        old.bodies.put(old.body,old.original);
        try{clearVisibleCache();}catch(ReflectiveOperationException error){failures.add("Failed to restore paired-control resource cache: "+error);}
    }
    private static final List<Entity> fixtures=new ArrayList<>();
    private static String graphics(){return System.getProperty("sparkmotors.visibilityGraphics","fancy");}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static View v(String name,double x,double y,double z,double tx,double ty,double tz,boolean marker){return new View(name,new Vec3(x,y,z),new Vec3(tx,ty,tz),marker);}
    private static List<View> views(BodyStyle b){
        double eye=b.roof()-.18,mid=b.belt()+.17;
        return List.of(
            v("through-left",-2.1,eye,0,0,eye,0,true),
            v("through-right",2.1,eye,0,0,eye,0,true),
            v("through-windshield",0,eye,3.8,0,eye,0,true),
            v("inside-out-windshield",0,mid,-.25,0,mid,3.4,true),
            v("interior-dashboard",.20,Math.min(b.roof()-.1,b.belt()+.21),-.70,-.35,b.belt()-.08,.36,false),
            v("grazing-two-windows",-2.0,b.belt()+.24,-1.1,.6,b.belt()+.17,.30,false));
    }
    private static void server(Minecraft mc,Consumer<ServerPlayer> operation){
        var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{operation.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid));}catch(Throwable error){error.printStackTrace();serverError=error.toString();}finally{pending=false;}});
    }
    private static void clearFixtures(){for(var e:fixtures)e.discard();fixtures.clear();}
    private static void marker(ServerPlayer p,Vec3 pos){
        var entity=EntityType.BLOCK_DISPLAY.create(p.level());require(entity!=null,"Could not create vanilla block display");
        var tag=new CompoundTag();entity.saveWithoutId(tag);
        tag.put("block_state",NbtUtils.writeBlockState(Blocks.LIME_CONCRETE.defaultBlockState()));
        var transform=new CompoundTag();var scale=new ListTag();var translation=new ListTag();var rotation=new ListTag();
        for(int i=0;i<4;i++)rotation.add(FloatTag.valueOf(i==3?1:0));
        transform.put("left_rotation",rotation);transform.put("right_rotation",rotation.copy());
        for(int i=0;i<3;i++){scale.add(FloatTag.valueOf(.16f));translation.add(FloatTag.valueOf(-.08f));}
        transform.put("scale",scale);transform.put("translation",translation);tag.put("transformation",transform);
        var brightness=new CompoundTag();brightness.putInt("block",15);brightness.putInt("sky",15);tag.put("brightness",brightness);
        entity.load(tag);
        var persisted=new CompoundTag();entity.saveWithoutId(persisted);
        var actual=persisted.getCompound("transformation").getList("scale",Tag.TAG_FLOAT);
        require(actual.size()==3&&Math.abs(actual.getFloat(0)-.16f)<1e-5,"Vanilla witness transformation was not accepted: "+persisted.get("transformation"));
        entity.moveTo(pos.x,pos.y,pos.z,0,0);p.serverLevel().addFreshEntity(entity);fixtures.add(entity);
    }
    private static void aim(Minecraft mc){
        if(target==null)return;var d=target.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        mc.player.yRotO=mc.player.getYRot();mc.player.xRotO=mc.player.getXRot();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("sparkmotors.clientVisibility")||done)return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        try{
            require(serverError==null,"Server fixture: "+serverError);require((System.nanoTime()-START)/1e9<1100,"Visibility capture timeout");
            if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
            if(!creating&&mc.screen instanceof TitleScreen){
                creating=true;mc.options.graphicsMode().set(graphics().equals("fabulous")?GraphicsStatus.FABULOUS:graphics().equals("fast")?GraphicsStatus.FAST:GraphicsStatus.FANCY);
                mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);mc.options.framerateLimit().set(60);mc.options.hideGui=true;mc.options.bobView().set(false);
                mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
                mc.createWorldOpenFlows().createFreshLevel("visibility-"+System.currentTimeMillis(),new LevelSettings("Visibility regression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(421,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),null);return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||pending||comparison!=null)return;
            if(stage==null){pending=true;server(mc,p->{
                stage=p.blockPosition();for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++){
                    p.serverLevel().setBlock(stage.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    // Neutral backdrops exclude grass from green-witness pixel measurements.
                    if(Math.abs(x)==7||Math.abs(z)==7)for(int y=0;y<5;y++)p.serverLevel().setBlock(stage.offset(x,y,z),Blocks.WHITE_CONCRETE.defaultBlockState(),3);
                }
                p.serverLevel().setDayTime(6000);p.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.serverLevel().getServer());p.setGameMode(GameType.SPECTATOR);
            });return;}
            if(job>=34){if(++finishing>30)finish(mc);return;}
            if(ticks==0){
                pending=true;frames=0;server(mc,p->{
                    clearFixtures();var origin=new Vec3(stage.getX()+.5,stage.getY()+.05,stage.getZ()+.5);
                    if(job<18){
                        var body=BodyStyle.values()[job/3];var type=TYPES[job%3];
                        if(carId<0){var c=AutoPropulsionAge.CAR.get().create(p.level());c.moveTo(origin.x,origin.y,origin.z,0,0);c.initializePowertrain(type,.45);c.initializeBodyStyle(body);c.setOwner(p.getUUID());p.serverLevel().addFreshEntity(c);carId=c.getId();}
                        var c=(CarEntity)p.level().getEntity(carId);origin=c.position();var shot=views(body).get(view);target=origin.add(shot.target);witness=shot.probe;
                        if(witness)marker(p,target);
                        var eye=origin.add(shot.eye);p.teleportTo(eye.x,eye.y-p.getEyeHeight(),eye.z);
                    }else{
                        var base=stage;var tier=ChargingModel.Tier.values()[(job-18)/4];var facing=new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}[(job-18)%4];
                        p.serverLevel().setBlock(base.below(),Blocks.WHITE_CONCRETE.defaultBlockState(),3);
                        p.serverLevel().setBlock(base,Electrification.CHARGERS.get(tier).get().defaultBlockState().setValue(ChargerBlock.FACING,facing),3);
                        target=new Vec3(base.getX()+.5,base.getY()+.12,base.getZ()+.5);witness=false;
                        p.teleportTo(target.x+(view==0?1.25:-1.25),base.getY()+(view==0?.28:1.55)-p.getEyeHeight(),target.z+(view==0?1.25:-1.25));
                    }
                });ticks=1;return;
            }
            aim(mc);ticks++;
            if(job<18&&!(mc.level.getEntity(carId) instanceof CarEntity))return;
            if(ticks>=25&&frames>=8&&capture==null){
                capture=job<18?"visibility-"+graphics()+"-"+BodyStyle.values()[job/3].id()+"-"+TYPES[job%3].id()+"-"+views(BodyStyle.values()[job/3]).get(view).name+".png":
                    "visibility-"+graphics()+"-charger-"+ChargingModel.Tier.values()[(job-18)/4].id()+"-"+((job-18)%4)+"-"+view+".png";
                ticks=-1000;
            }
            if(ticks<0&&capture==null){
                int count=job<18?6:2;
                if(++view==count){
                    int old=carId;carId=-1;pending=true;
                    server(mc,p->{if(old>=0){var c=p.level().getEntity(old);if(c!=null)c.discard();}clearFixtures();p.serverLevel().removeBlock(stage,false);});job++;view=0;
                }
                ticks=0;
            }
        }catch(Throwable error){error.printStackTrace();failures.add(error.toString());finish(mc);}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!Boolean.getBoolean("sparkmotors.clientVisibility")||done)return;frames++;
        var mc=Minecraft.getInstance();
        if(comparison!=null){
            if(--comparisonWait>0)return;
            var pair=comparison;
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                var pixels=region(image);int controlLime=limeCount(pixels),changed=0,witnessChanged=0;
                for(int i=0;i<pixels.length;i++){
                    int delta=0;for(int shift:new int[]{0,8,16})delta+=Math.abs((pixels[i]>>shift&255)-(pair.pixels[i]>>shift&255));
                    if(delta>=6){changed++;if(lime(pixels[i]))witnessChanged++;}
                }
                require(controlLime>=25,"Paired no-glass witness missing: fixture or opaque geometry blocks sightline "+pair.name);
                require(changed>=50&&witnessChanged>=10,"Glass must actually cover and tint the sightline, not disappear entirely: "+pair.name+" changed="+changed+" witnessChanged="+witnessChanged);
                String control=pair.name.replace(".png","-control-no-glass.png");
                image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(control));
                captures.add(Map.of("name",pair.name,"witness",true,"limePixels",pair.limePixels,"control",control,"controlLimePixels",controlLime,"glassChangedPixels",changed,"tintedWitnessPixels",witnessChanged));
                System.out.println("VISIBILITY_CONTROL "+pair.name+" withoutGlass="+controlLime+" tintedWitness="+witnessChanged);
            }catch(Exception error){failures.add(pair.name+": "+error);}finally{restoreGlass();}
            return;
        }
        if(capture==null)return;String name=capture;
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int[] pixels=witness?region(image):new int[0];int lime=limeCount(pixels);
            if(witness&&lime<25)failures.add(name+": expected visible lime witness through glass; pixels="+lime);
            var dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);image.writeToFile(dir.resolve(name));
            if(witness){comparison=hideGlassForControl(name,lime,pixels,BodyStyle.values()[job/3]);comparisonWait=4;}
            else {
                int sky=0;
                if(job>=18){
                    for(int y=image.getHeight()/2+30;y<image.getHeight()/2+180;y++)for(int x=image.getWidth()/2-300;x<image.getWidth()/2+300;x++){
                        int abgr=image.getPixelRGBA(x,y),r=abgr&255,g=abgr>>8&255,b=abgr>>16&255;
                        if(b>140&&b>r*1.18&&b>g*1.08)sky++;
                    }
                    if(sky>8)failures.add(name+": supporting terrain is missing near charger base; sky pixels="+sky);
                }
                captures.add(Map.of("name",name,"witness",false,"limePixels",0,"charger",job>=18,"baseSkyPixels",sky));
            }
            System.out.println("VISIBILITY_FRAME "+name+" witness="+witness+" pixels="+lime);
        }catch(Exception e){failures.add(name+": "+e);}finally{capture=null;}
    }
    private static void finish(Minecraft mc){
        if(done)return;done=true;restoreGlass();
        String result=failures.isEmpty()&&captures.size()==140?"PASS":"FAIL";
        try{
            var data=Map.of("result",result,"graphics",graphics(),"captures",captures,"failures",failures,"seconds",(System.nanoTime()-START)/1e9);
            Files.writeString(mc.gameDirectory.toPath().resolve("visibility-"+graphics()+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
            System.out.println("VISIBILITY_RESULT "+result+" frames="+captures.size()+" failures="+failures.size());
        }catch(Exception e){throw new IllegalStateException(e);}mc.stop();
    }
}
