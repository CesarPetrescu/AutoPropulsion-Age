package com.photonspark.autopropulsion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Only registered with -Dautopropulsion.smokeServer=true in the isolated development profile. */
public final class SmokeServer {
    public static void register(){NeoForge.EVENT_BUS.addListener(SmokeServer::login);}
    private static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var level=player.serverLevel();
        for(var e:level.getEntitiesOfClass(VehicleEntity.class,player.getBoundingBox().inflate(1000)))e.discard();
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(int x=-10;x<=10;x++)for(int z=-12;z<=210;z++){
            level.setBlockAndUpdate(new BlockPos(x,63,z),Content.ASPHALT.get().defaultBlockState());
            for(int y=64;y<=68;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        player.setGameMode(GameType.CREATIVE);player.teleportTo(.5,65,.5);player.setYRot(0);player.setXRot(16);
        var car=Content.VEHICLE.get().create(level);if(car==null)throw new IllegalStateException("vehicle registration failed");
        car.moveTo(.5,64,.5,0,0);car.setOwner(player.getUUID());level.addFreshEntity(car);player.startRiding(car,true);
        AutoPropulsion.LOG.info("AUTOPROPULSION_SMOKE_SERVER_READY player={} parts={}",player.getGameProfile().getName(),PartCatalog.size());
    }
    private SmokeServer(){}
}
