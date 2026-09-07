package com.photonspark.autopropulsion;
import com.mojang.authlib.GameProfile;
import com.photonspark.autopropulsion.sim.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder(AutoPropulsion.ID)
@PrefixGameTestTemplate(false)
public final class VehicleGameTests {
    private static VehicleEntity car(GameTestHelper h){var c=Content.VEHICLE.get().create(h.getLevel());h.assertTrue(c!=null,"entity type registered");var p=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3));c.setPos(p.getX(),p.getY(),p.getZ());return c;}
    @GameTest(template="empty") public static void catalogueLoaded(GameTestHelper h){h.assertTrue(PartCatalog.size()>=140,"140+ definitions loaded");for(String id:BuiltinCatalog.FUNCTIONAL_IDS)h.assertTrue(PartCatalog.get("autopropulsion:"+id).functional(),"functional definition "+id);h.succeed();}
    @GameTest(template="empty") public static void starterAssembly(GameTestHelper h){var c=car(h);h.assertTrue(c.installed().size()==7,"seven default slots");h.assertTrue(c.simulation().state().fuelL()==45,"starter fuel");h.succeed();}
    @GameTest(template="empty") public static void persistenceParksSafely(GameTestHelper h){var c=car(h);var owner=UUID.fromString("8f6b38ce-79d6-4f04-a207-08e47d7d9940");c.setOwner(owner);c.simulation().restore(new VehicleSimulation.State(20,3400,3,22,355,360,.5,73,1234,true,"TEST"));CompoundTag tag=new CompoundTag();c.addAdditionalSaveData(tag);var restored=car(h);restored.readAdditionalSaveData(tag);var s=restored.simulation().state();h.assertTrue(owner.equals(restored.owner()),"owner roundtrip");h.assertTrue(s.fuelL()==22&&s.health()==73&&s.odometerM()==1234,"wear/fluid/odometer roundtrip");h.assertTrue(!s.running()&&s.speedMs()==0,"reload parked with ignition off");h.assertTrue(c.installed().equals(restored.installed()),"assembly roundtrip");h.succeed();}
    @GameTest(template="empty") public static void rejectsMalformedPersistence(GameTestHelper h){var c=car(h);CompoundTag t=new CompoundTag();t.putInt("DataVersionAP",1);t.putDouble("BoostSetting",Double.NaN);c.readAdditionalSaveData(t);h.assertTrue(Double.isFinite(c.simulation().state().fuelL()),"NaN cannot poison state");h.succeed();}
    @GameTest(template="empty") public static void itemDefinitionRoundtrip(GameTestHelper h){for(String id:BuiltinCatalog.IDS){ItemStack stack=ComponentItem.stack(id);h.assertTrue(ComponentItem.definition(stack).equals("autopropulsion:"+id),"part data component "+id);}h.succeed();}
    @GameTest(template="empty") public static void ownershipAndInstallation(GameTestHelper h){var c=car(h);var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.fromString("8f6b38ce-79d6-4f04-a207-08e47d7d9941"),"AP_Owner"));p.setGameMode(GameType.SURVIVAL);p.setPos(c.getX(),c.getY(),c.getZ());c.setOwner(p.getUUID());var stack=ComponentItem.stack("forged_connecting_rods");h.assertTrue(c.installPart(p,stack),"owner may install while parked");h.assertTrue(stack.isEmpty(),"survival transaction consumes exactly one part");h.assertTrue(c.installed().get("connecting_rods").endsWith("forged_connecting_rods"),"replacement persisted");h.assertTrue(!c.installPart(p,ComponentItem.stack("rotor")),"catalogue-only rotary rejected");h.succeed();}
    @GameTest(template="empty") public static void remoteInstallationRejected(GameTestHelper h){var c=car(h);var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.fromString("8f6b38ce-79d6-4f04-a207-08e47d7d9942"),"AP_Remote"));c.setOwner(p.getUUID());p.setPos(c.getX()+100,c.getY(),c.getZ());h.assertTrue(!c.installPart(p,ComponentItem.stack("race_camshaft")),"remote mutation rejected");h.assertTrue(!c.receiveInput(p,VehicleInput.IDLE),"non-driver input rejected");h.succeed();}
    @GameTest(template="empty") public static void runningInstallationRejected(GameTestHelper h){var c=car(h);var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.fromString("8f6b38ce-79d6-4f04-a207-08e47d7d9943"),"AP_Running"));c.setOwner(p.getUUID());p.setPos(c.getX(),c.getY(),c.getZ());c.simulation().toggleEngine();h.assertTrue(!c.installPart(p,ComponentItem.stack("race_camshaft")),"cannot replace moving internals while running");h.succeed();}
}
