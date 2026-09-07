package com.photonspark.autopropulsion;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Smoke-test world changes are gated by an explicit JVM property, never normal gameplay. */
public final class DevelopmentHooks {
    private DevelopmentHooks() {}
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("apa").requires(source -> source.hasPermission(2))
            .then(Commands.literal("spawn").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                VehicleEntity car = new VehicleEntity(AutoPropulsion.HATCH.get(), player.level());
                car.moveTo(player.getX(), player.getY(), player.getZ() + 4, player.getYRot(), 0);
                car.setOwner(player.getUUID()); car.installDefaults();
                player.level().addFreshEntity(car);
                context.getSource().sendSuccess(() -> Component.literal("Spawned Hatch 01; right-click to drive. E starts, R/F shift."), false);
                return 1;
            }))
            .then(Commands.literal("status").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("AutoPropulsion 1.21.1 alpha; loaded part definitions: " + PartCatalog.size()), false);
                return PartCatalog.size();
            })));
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("apa.serverSmoke") || !(event.getEntity() instanceof ServerPlayer player)) return;
        var level = player.serverLevel();
        // This runs only in the disposable CI world; normal servers never get a generated test track.
        for (int x = -6; x <= 6; x++) for (int z = -12; z <= 190; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 63, z), Blocks.GRAY_CONCRETE.defaultBlockState());
            for (int y = 64; y <= 67; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(.5, 64, -5);
        level.setBlockAndUpdate(new BlockPos(4, 64, 2), AutoPropulsion.GARAGE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(-4, 64, 2), AutoPropulsion.DYNO.get().defaultBlockState());
        VehicleEntity car = new VehicleEntity(AutoPropulsion.HATCH.get(), level);
        car.moveTo(.5, 64, .5, 0, 0); car.setOwner(player.getUUID()); car.installDefaults();
        level.addFreshEntity(car); player.startRiding(car, true);
        player.getInventory().add(new net.minecraft.world.item.ItemStack(AutoPropulsion.LAPTOP.get()));
        player.getInventory().add(PartItem.stack("autopropulsion:turbo_street"));
        AutoPropulsion.LOG.info("APA_SMOKE_SERVER_READY player={} vehicle={}", player.getGameProfile().getName(), car.getId());
    }
}
