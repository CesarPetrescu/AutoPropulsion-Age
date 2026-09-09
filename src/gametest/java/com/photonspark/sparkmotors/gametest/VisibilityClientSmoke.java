package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.charging.*;
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
        var transform=new CompoundTag();var scale=new ListTag();var translation=new ListTag();
        for(int i=0;i<3;i++){scale.add(FloatTag.valueOf(.16f));translation.add(FloatTag.valueOf(-.08f));}
        transform.put("scale",scale);transform.put("translation",translation);tag.put("transformation",transform);
        var brightness=new CompoundTag();brightness.putInt("block",15);brightness.putInt("sky",15);tag.put("brightness",brightness);
        entity.load(tag);entity.moveTo(pos.x,pos.y,pos.z,0,0);p.serverLevel().addFreshEntity(entity);fixtures.add(entity);
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
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||pending)return;
            if(stage==null){pending=true;server(mc,p->{
                stage=p.blockPosition();for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)p.serverLevel().setBlock(stage.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
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
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int lime=0;
            if(witness){
                for(int y=image.getHeight()/2-90;y<image.getHeight()/2+90;y++)for(int x=image.getWidth()/2-90;x<image.getWidth()/2+90;x++){
                    int abgr=image.getPixelRGBA(x,y),r=abgr&255,g=abgr>>8&255,b=abgr>>16&255;
                    if(g>80&&g>r*1.25&&g>b*1.3)lime++;
                }
                if(lime<25)failures.add(name+": expected visible lime witness through glass; pixels="+lime);
            }
            var dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);image.writeToFile(dir.resolve(name));
            captures.add(Map.of("name",name,"witness",witness,"limePixels",lime));
            System.out.println("VISIBILITY_FRAME "+name+" witness="+witness+" pixels="+lime);
        }catch(Exception e){failures.add(name+": "+e);}finally{capture=null;}
    }
    private static void finish(Minecraft mc){
        if(done)return;done=true;
        String result=failures.isEmpty()&&captures.size()==140?"PASS":"FAIL";
        try{
            var data=Map.of("result",result,"graphics",graphics(),"captures",captures,"failures",failures,"seconds",(System.nanoTime()-START)/1e9);
            Files.writeString(mc.gameDirectory.toPath().resolve("visibility-"+graphics()+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
            System.out.println("VISIBILITY_RESULT "+result+" frames="+captures.size()+" failures="+failures.size());
        }catch(Exception e){throw new IllegalStateException(e);}mc.stop();
    }
}
