package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.item.MechanicalData;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;

/** Two real network clients; files coordinate the harness only, all service requests use game packets. */
@EventBusSubscriber(modid="sparkmotors")
public final class MultiplayerServer {
    private static int stage,ticks,total,unauthorizedAt=-1;private static CarEntity first,second;private static PartInstance used;
    public static Path root(){return Path.of(System.getProperty("sparkmotors.multiRoot","run"));}
    private static void write(String name,String value){try{Files.createDirectories(root());Path temporary=root().resolve(name+".tmp");Files.writeString(temporary,value);Files.move(temporary,root().resolve(name),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception ex){throw new RuntimeException(ex);}}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void advance(int next){stage=next;ticks=0;write("stage.txt",Integer.toString(stage));System.out.println("MULTIPLAYER_SERVER_STAGE "+stage);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(!Boolean.getBoolean("sparkmotors.multiServer"))return;var server=e.getServer();ticks++;total++;
        try{
            if(total==1)write("ready.txt","ready");
            if(total>6000)throw new IllegalStateException("Multiplayer timeout at stage "+stage);
            var a=server.getPlayerList().getPlayerByName("MechanicA");var b=server.getPlayerList().getPlayerByName("MechanicB");
            if(a==null||b==null){if(stage>=5&&Files.exists(root().resolve("client-A.pass"))&&Files.exists(root().resolve("client-B.pass"))){write("result.json","{\"passed\":true,\"clients\":2,\"checks\":[\"ownership rejection\",\"live entity synchronization\",\"native item drop and pickup\",\"used part reinstallation in another owned car\"]}");server.halt(false);}return;}
            if(stage==0){
                var level=server.overworld();for(int x=-5;x<18;x++)for(int z=3;z<16;z++)level.setBlock(new net.minecraft.core.BlockPos(x,64,z),net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(),3);
                a.setGameMode(GameType.SURVIVAL);b.setGameMode(GameType.SURVIVAL);a.getInventory().clearContent();b.getInventory().clearContent();a.teleportTo(10,65,8);b.teleportTo(6,65,8);
                first=car(a,8);second=car(b,0);used=first.mechanics().get("cooling.upper_hose").condition(.45,.60,PartInstance.LEAK);first.setMechanics(first.mechanics().with("cooling.upper_hose",used));
                write("cars.txt",first.getId()+","+second.getId());advance(1);
            }else if(stage==1){
                if(unauthorizedAt<0&&Files.exists(root().resolve("unauthorized-request.sent")))unauthorizedAt=ticks;
                if(unauthorizedAt>=0&&ticks-unauthorizedAt>65){
                    check(first.mechanics().get("cooling.upper_hose").equals(used),"Unauthorized client removed owner A's hose");check(b.getInventory().isEmpty(),"Unauthorized request changed inventory");advance(2);
                }
            }else if(stage==2&&first.mechanics().get("cooling.upper_hose")==null){
                check(a.getInventory().items.stream().anyMatch(s->MechanicalData.get(s)!=null&&used.equals(MechanicalData.get(s).get("cooling.upper_hose"))),"Owner did not receive exact worn hose");advance(3);
            }else if(stage==3){
                var drops=server.overworld().getEntitiesOfClass(ItemEntity.class,first.getBoundingBox().inflate(8));
                for(var item:drops){var data=MechanicalData.get(item.getItem());if(data!=null&&data.parts().values().stream().anyMatch(p->p.id().equals(used.id()))){
                    // Keep the original owner out of pickup range; the recipient still
                    // collects a real ItemEntity through Minecraft's ordinary collision path.
                    a.teleportTo(10,65,13);item.setNoPickUpDelay();b.teleportTo(item.getX(),item.getY(),item.getZ());
                }}
                if(ticks%200==0)System.out.println("MULTIPLAYER_WAIT_FOR_PICKUP drops="+drops.size()+" ownerInventory="+a.getInventory().items+" recipientInventory="+b.getInventory().items);
                if(b.getInventory().items.stream().anyMatch(s->MechanicalData.get(s)!=null&&MechanicalData.get(s).parts().values().stream().anyMatch(p->p.id().equals(used.id())))){
                    b.teleportTo(2,65,8);advance(4);
                }
            }else if(stage==4&&used.equals(second.mechanics().get("cooling.upper_hose"))){
                check(first.mechanics().get("cooling.upper_hose")==null,"Original car was silently healed during trade");
                check(b.getInventory().items.stream().noneMatch(s->MechanicalData.get(s)!=null&&MechanicalData.get(s).parts().values().stream().anyMatch(p->p.id().equals(used.id()))),"Installed part duplicated in inventory");advance(5);
            }
            if(stage==5&&Files.exists(root().resolve("client-A.pass"))&&Files.exists(root().resolve("client-B.pass"))){write("result.json","{\"passed\":true,\"clients\":2,\"checks\":[\"ownership rejection\",\"live entity synchronization\",\"native item drop and pickup\",\"used part reinstallation in another owned car\"]}");server.halt(false);}
        }catch(Throwable failure){write("result.json","{\"passed\":false,\"stage\":"+stage+",\"error\":\""+failure.getMessage().replace("\"","'")+"\"}");failure.printStackTrace();server.halt(false);}
    }
    private static CarEntity car(ServerPlayer p,double x){var c=AutoPropulsionAge.CAR.get().create(p.serverLevel());c.moveTo(x,65.05,8,0,0);c.setOwner(p.getUUID());var tag=new CompoundTag();c.saveWithoutId(tag);tag.putBoolean("HoodOpen",true);c.load(tag);p.serverLevel().addFreshEntity(c);return c;}
}
