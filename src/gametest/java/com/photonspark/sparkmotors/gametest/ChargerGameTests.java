package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.charging.*;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class ChargerGameTests {
    private record Fixture(CarEntity car,ServerPlayer player,ChargerBlockEntity charger){}
    private Fixture fixture(GameTestHelper h){
        var car=AutoPropulsionAge.CAR.get().create(h.getLevel());var point=h.absoluteVec(new Vec3(8,2.05,8));
        car.moveTo(point.x,point.y,point.z,0,0);car.initializePowertrain(Powertrain.ELECTRIC_800,.25);h.getLevel().addFreshEntity(car);
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.SURVIVAL);p.moveTo(point);car.setOwner(p.getUUID());
        var pos=h.absolutePos(new BlockPos(11,2,8));h.getLevel().setBlock(pos,Electrification.CHARGERS.get(ChargingModel.Tier.ULTRA).get().defaultBlockState(),3);
        return new Fixture(car,p,(ChargerBlockEntity)h.getLevel().getBlockEntity(pos));
    }
    private void pair(GameTestHelper h,Fixture f){
        var cable=Electrification.CABLE.toStack();cable.set(DataComponents.CUSTOM_NAME,Component.literal("My cable"));f.player.setItemInHand(InteractionHand.MAIN_HAND,cable);
        var hit=new BlockHitResult(Vec3.atCenterOf(f.charger.getBlockPos()),Direction.UP,f.charger.getBlockPos(),false);
        cable.useOn(new UseOnContext(f.player,InteractionHand.MAIN_HAND,hit));
        f.car.interact(f.player,InteractionHand.MAIN_HAND);
        h.assertTrue(cable.isEmpty()&&f.charger.hasCable()&&f.car.plugged(),"Real item interactions escrow one cable");
    }
    private long drops(GameTestHelper h,Fixture f){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(f.charger.getBlockPos()).inflate(5)).stream().filter(e->e.getItem().is(Electrification.CABLE.get())).mapToInt(e->e.getItem().getCount()).sum();}
    @GameTest(template="test_track") public void cableReturnsFromCarExactlyOnce(GameTestHelper h){
        var f=fixture(h);pair(h,f);f.player.setShiftKeyDown(true);
        f.car.interactAt(f.player,new Vec3(0,1,1),InteractionHand.MAIN_HAND);
        h.assertTrue(!f.car.plugged()&&!f.charger.hasCable(),"Car-end unplug releases and returns cable");
        var returned=f.player.getInventory().items.stream().filter(s->s.is(Electrification.CABLE.get())).findFirst().orElseThrow();
        h.assertTrue(returned.getHoverName().getString().equals("My cable")&&!returned.has(DataComponents.CUSTOM_DATA),"Custom name preserved and stale pairing cleared");
        f.charger.unplug(f.player);f.charger.disconnect();h.assertTrue(f.player.getInventory().countItem(Electrification.CABLE.get())==1&&drops(h,f)==0,"Repeated unplug cannot duplicate");h.succeed();
    }
    @GameTest(template="test_track") public void fullInventoryDropsReturnedCable(GameTestHelper h){
        var f=fixture(h);pair(h,f);for(int i=0;i<36;i++)f.player.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        f.charger.unplug(f.player);f.charger.unplug(f.player);
        h.assertTrue(drops(h,f)==1&&!f.charger.hasCable()&&!f.car.plugged(),"Full inventory drops exactly one cable");h.succeed();
    }
    @GameTest(template="test_track") public void creativeFullInventoryDoesNotVoidCable(GameTestHelper h){
        var f=fixture(h);f.player.setGameMode(GameType.CREATIVE);pair(h,f);
        for(int i=0;i<36;i++)f.player.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        f.charger.unplug(f.player);f.charger.unplug(f.player);
        h.assertTrue(drops(h,f)==1&&!f.charger.hasCable(),"Creative overflow also drops exactly one cable instead of voiding it");h.succeed();
    }
    @GameTest(template="test_track") public void breakChargerDropsCableOnce(GameTestHelper h){
        var f=fixture(h);pair(h,f);h.getLevel().removeBlock(f.charger.getBlockPos(),false);f.charger.dropCable();
        h.assertTrue(drops(h,f)==1&&!f.car.plugged(),"Breaking charger releases car and drops physical cable once");h.succeed();
    }
    @GameTest(template="test_track") public void unloadReloadPreservesRecoverableCable(GameTestHelper h){
        var f=fixture(h);pair(h,f);f.charger.onChunkUnloaded();
        var tag=f.charger.saveWithFullMetadata(h.getLevel().registryAccess());var loaded=new ChargerBlockEntity(f.charger.getBlockPos(),f.charger.getBlockState());
        loaded.setLevel(h.getLevel());loaded.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(!f.car.plugged()&&!loaded.connected()&&loaded.hasCable(),"Unload expires connection but persists escrow");
        loaded.unplug(f.player);loaded.unplug(f.player);
        h.assertTrue(f.player.getInventory().countItem(Electrification.CABLE.get())==1,"Reloaded cable recoverable exactly once");h.succeed();
    }
    @GameTest(template="test_track") public void invalidAndOccupiedConnectionsKeepItems(GameTestHelper h){
        var f=fixture(h);var cable=Electrification.CABLE.toStack();f.car.setOwner(java.util.UUID.randomUUID());
        h.assertTrue(!f.charger.connectCable(f.player,f.car,cable)&&cable.getCount()==1&&!f.charger.hasCable(),"Foreign car does not consume cable");
        f.car.setOwner(f.player.getUUID());pair(h,f);
        h.assertTrue(!f.charger.connectCable(f.player,f.car,cable)&&cable.getCount()==1,"Repeat pairing cannot consume another cable");
        f.player.moveTo(f.player.position().add(100,0,0));f.charger.unplug(f.player);
        h.assertTrue(f.charger.hasCable()&&f.car.plugged(),"Remote removal cannot steal escrow");h.succeed();
    }
    @GameTest(template="test_track") public void endpointLossKeepsCableAndLegacyDoesNotCreateOne(GameTestHelper h){
        var f=fixture(h);pair(h,f);f.car.discard();f.charger.serverTick();
        h.assertTrue(!f.charger.connected()&&f.charger.hasCable(),"Lost vehicle leaves cable recoverable at charger");
        f.charger.unplug(f.player);h.assertTrue(f.player.getInventory().countItem(Electrification.CABLE.get())==1,"Lost endpoint returns paid item");
        f.charger.disconnect();f.charger.unplug(f.player);h.assertTrue(f.player.getInventory().countItem(Electrification.CABLE.get())==1,"Legacy/no-escrow unplug creates no free cable");h.succeed();
    }
    @GameTest(template="test_track") public void foreignPlayerCannotRecoverStoredCable(GameTestHelper h){
        var f=fixture(h);pair(h,f);var other=h.makeMockServerPlayerInLevel();other.setGameMode(GameType.SURVIVAL);other.setUUID(java.util.UUID.randomUUID());other.moveTo(f.player.position());
        h.assertTrue(!f.charger.mayUse(other),"Fixture is not an authorized charger owner");f.charger.unplug(other);
        h.assertTrue(f.charger.hasCable()&&f.car.plugged()&&other.getInventory().countItem(Electrification.CABLE.get())==0,"Foreign player cannot recover another player's cable");h.succeed();
    }
    @GameTest(template="test_track") public void legacyPairingDoesNotMintCable(GameTestHelper h){
        var f=fixture(h);h.assertTrue(f.charger.connect(f.player,f.car),"Legacy/test pairing supported");f.charger.unplug(f.player);
        h.assertTrue(!f.car.plugged()&&f.player.getInventory().countItem(Electrification.CABLE.get())==0&&drops(h,f)==0,"Legacy pairing has no escrow and cannot create a cable");h.succeed();
    }
}
