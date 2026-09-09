package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.BodyStyle;
import com.photonspark.sparkmotors.sim.DriveConfig;
import com.photonspark.sparkmotors.sim.PowertrainTopology;
import com.photonspark.sparkmotors.sim.electric.Powertrain;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real-world composition matrix. The pure simulation suite crosses engine
 * families/grades/induction too; this test deliberately spends the native world
 * budget on every body x powertrain x layout x differential combination and
 * verifies mounting, ignition, motion, braking, energy use and persistence as one
 * lifecycle rather than as isolated feature tests.
 */
@GameTestHolder("sparkmotors")
@PrefixGameTestTemplate(false)
public final class GeneratedCarMatrixGameTests {
    private static final int CASE_TICKS = 130;
    private static final int CASES = BodyStyle.values().length * Powertrain.values().length
        * DriveConfig.Layout.values().length * DriveConfig.Differential.values().length;

    private static CarEntity car(GameTestHelper h) {
        var c = AutoPropulsionAge.CAR.get().create(h.getLevel());
        var p = h.absoluteVec(new Vec3(8, 2.05, 8));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    private static int split(BodyStyle body, Powertrain type, DriveConfig.Layout layout,
                             DriveConfig.Differential differential) {
        if (layout != DriveConfig.Layout.AWD) return 50;
        return 20 + 20 * ((body.ordinal() + type.ordinal() + differential.ordinal()) & 3);
    }

    private static void action(CarEntity c, net.minecraft.server.level.ServerPlayer p, int action) {
        c.tickCount += 4;
        c.action(p, action, 0, 0);
    }

    private static String label(int job, BodyStyle body, Powertrain type, DriveConfig drive) {
        return "case=" + job + " body=" + body.id() + " powertrain=" + type.id()
            + " layout=" + drive.layout() + " diff=" + drive.differential() + " split=" + drive.frontPercent();
    }

    @GameTest(template = "test_track", timeoutTicks = 42000)
    public void everyBodyPowertrainLayoutDifferentialCombinationRunsAsOneLifecycle(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        CarEntity[] active = {null};
        int[] ticks = {0};
        float[] peak = {0};
        double[] startEnergy = {0};
        double[] startX = {0};
        double[] startZ = {0};

        h.onEachTick(() -> {
            int t = ticks[0]++, job = t / CASE_TICKS, step = t % CASE_TICKS;
            if (job >= CASES) return;

            int differentialIndex = job % DriveConfig.Differential.values().length;
            int layoutIndex = (job / DriveConfig.Differential.values().length) % DriveConfig.Layout.values().length;
            int typeIndex = (job / (DriveConfig.Differential.values().length * DriveConfig.Layout.values().length)) % Powertrain.values().length;
            int bodyIndex = job / (DriveConfig.Differential.values().length * DriveConfig.Layout.values().length * Powertrain.values().length);
            var body = BodyStyle.values()[bodyIndex];
            var type = Powertrain.values()[typeIndex];
            var layout = DriveConfig.Layout.values()[layoutIndex];
            var differential = DriveConfig.Differential.values()[differentialIndex];
            var drive = new DriveConfig(layout, differential, split(body, type, layout, differential));
            String caseLabel = label(job, body, type, drive);

            if (step == 0) {
                if (active[0] != null) {
                    player.stopRiding();
                    active[0].discard();
                }
                var c = car(h);
                active[0] = c;
                c.initializeBodyStyle(body);
                c.setDriveConfig(drive);
                c.initializePowertrain(type, .63);
                h.assertTrue(c.bodyStyle() == body && c.powertrain() == type && c.driveConfig().equals(drive),
                    "Generated identity mismatch before drive: " + caseLabel);
                h.assertTrue(PowertrainTopology.availability(c.mechanics(), type, drive) > .05,
                    "Generated car has no fresh torque path: " + caseLabel);

                var before = new CompoundTag();
                c.saveWithoutId(before);
                var probe = AutoPropulsionAge.CAR.get().create(h.getLevel());
                probe.load(before);
                h.assertTrue(probe.bodyStyle() == body && probe.powertrain() == type && probe.driveConfig().equals(drive),
                    "Configuration changed on pre-drive save/load: " + caseLabel);
                h.assertTrue(probe.mechanics().equals(c.mechanics()),
                    "Mechanical identity changed on pre-drive save/load: " + caseLabel);

                player.moveTo(c.position());
                c.setOwner(player.getUUID());
                h.assertTrue(player.startRiding(c, true), "Driver could not mount: " + caseLabel);
                action(c, player, CarPackets.IGNITION);
                h.assertTrue(c.ignition(), "Configured car refused ignition/READY: " + caseLabel);
                peak[0] = 0;
                startX[0] = c.getX();
                startZ[0] = c.getZ();
                startEnergy[0] = type.electric() ? c.tractionBattery().energyJ() : c.fuel();
            }

            var c = active[0];
            float steer = ((job % 5) - 2) * .04f;
            c.receiveInput(step < 10 ? 4 : step < 60 ? 1 : 2, step >= 18 && step < 48 ? steer : 0);
            peak[0] = Math.max(peak[0], c.horizontalSpeed());

            if (step == CASE_TICKS - 2) {
                h.assertTrue(peak[0] > 1.5f && Math.abs(c.speed()) < .45f,
                    "Composed car did not drive and brake: " + caseLabel + " peak=" + peak[0] + " speed=" + c.speed());
                double displacement = Math.hypot(c.getX() - startX[0], c.getZ() - startZ[0]);
                h.assertTrue(displacement > 1.0,
                    "Composed car did not make world progress: " + caseLabel + " displacement=" + displacement);
                if (type.electric()) h.assertTrue(c.tractionBattery().energyJ() < startEnergy[0],
                    "Electric/hybrid lifecycle spent no battery energy: " + caseLabel);
                else h.assertTrue(c.fuel() < startEnergy[0], "Combustion lifecycle spent no fuel: " + caseLabel);

                var after = new CompoundTag();
                c.saveWithoutId(after);
                var probe = AutoPropulsionAge.CAR.get().create(h.getLevel());
                probe.load(after);
                h.assertTrue(probe.bodyStyle() == body && probe.powertrain() == type && probe.driveConfig().equals(drive),
                    "Configuration changed after drive save/load: " + caseLabel);
                h.assertTrue(probe.mechanics().equals(c.mechanics()),
                    "Used mechanical state changed after drive save/load: " + caseLabel);
                h.assertTrue(Math.abs(probe.fuel() - c.fuel()) < 1e-6,
                    "Fuel changed after drive save/load: " + caseLabel);
                if (type.electric()) h.assertTrue(Math.abs(probe.tractionBattery().energyJ() - c.tractionBattery().energyJ()) < 1e-3,
                    "Battery energy changed after drive save/load: " + caseLabel);

                System.out.println("GENERATED_CAR_MATRIX_CASE_PASS " + caseLabel);
                if (job == CASES - 1) {
                    player.stopRiding();
                    c.discard();
                    System.out.println("GENERATED_CAR_MATRIX_SERVER_PASS " + CASES);
                    h.succeed();
                }
            }
        });
    }
}
