package com.photonspark.autopropulsion;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AutoPropulsion.ID)
@PrefixGameTestTemplate(false)
public final class VehicleGameTests {
    private static VehicleEntity car(GameTestHelper helper) {
        VehicleEntity car = new VehicleEntity(AutoPropulsion.HATCH.get(), helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        car.setPos(at.getX() + .5, at.getY(), at.getZ() + .5); car.setNoGravity(true); car.installDefaults();
        helper.getLevel().addFreshEntity(car); return car;
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void registriesAndDefaultAssembly(GameTestHelper helper) {
        VehicleEntity car = car(helper);
        helper.assertTrue(PartCatalog.size() >= 136, "Builtin catalogue must be loaded on the dedicated server");
        helper.assertTrue(car.assembly().installed().size() == 7, "Default car must contain seven installable parts");
        helper.assertTrue(car.assembly().missingRequired().isEmpty(), "Default car has missing required slots");
        helper.assertTrue(car.simulation().spec().structuralTorqueNm() == 260, "Stock rods must set the structural limit");
        car.discard(); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nbtRoundTrip(GameTestHelper helper) {
        VehicleEntity car = car(helper); car.simulation().refuel(-7.25);
        CompoundTag saved = new CompoundTag(); car.saveWithoutId(saved);
        VehicleEntity restored = new VehicleEntity(AutoPropulsion.HATCH.get(), helper.getLevel()); restored.load(saved);
        helper.assertTrue(Math.abs(restored.simulation().snapshot().fuel() - 22.75) < .0001, "Fuel must survive NBT serialization");
        helper.assertTrue(restored.assembly().installed().size() == 7, "Part tree must survive NBT serialization");
        helper.assertTrue(!restored.simulation().snapshot().running(), "Restored vehicles must be safely parked");
        car.discard(); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void modelStudiesCannotInstall(GameTestHelper helper) {
        VehicleEntity car = car(helper);
        var reasons = car.assembly().reasons("engine/turbo", PartCatalog.get("autopropulsion:model_turbocharger"));
        helper.assertTrue(reasons.stream().anyMatch(s -> s.contains("model_only")), "A model study cannot masquerade as functioning hardware");
        car.discard(); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fullLengthCollisionBox(GameTestHelper helper) {
        VehicleEntity car = car(helper);
        helper.assertTrue(car.getBoundingBox().getZsize() >= 4.3, "Collision bounds must cover the complete body");
        helper.assertTrue(car.getBoundingBox().getXsize() >= 1.8, "Collision bounds must cover the wheel track");
        car.discard(); helper.succeed();
    }
}
