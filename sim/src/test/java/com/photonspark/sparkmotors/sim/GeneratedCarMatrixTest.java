package com.photonspark.sparkmotors.sim;

import com.photonspark.sparkmotors.sim.electric.ElectricDynamics;
import com.photonspark.sparkmotors.sim.electric.Powertrain;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generated integration matrix for the pure simulation/state layer.
 *
 * The expensive Minecraft GameTest layer has a companion generated matrix.  This
 * layer can afford to cross every body, powertrain, drive layout, differential,
 * engine family, engine grade and induction mode while advancing the same physics
 * classes used by CarEntity.  Failures include a deterministic case id so a CI
 * failure can be reproduced without guessing which generated combination failed.
 */
class GeneratedCarMatrixTest {
    private static final boolean[] CONTACTS = {true, true, true, true};
    private static final double[] GRIP = {.95, .95, .95, .95};
    private static final double[] TRAVEL = new double[4];
    private static final long FUZZ_SEED = 424242L;

    private record MatrixCase(BodyStyle body, Powertrain type, DriveConfig drive,
                              EngineFamily family, int grade, int induction) {
        boolean engineEquipped() { return !type.electric() || type.hybrid(); }
        int parts() { return engineEquipped() ? EnginePart.boosted(induction) : EnginePart.stock(); }
        int config() { return engineEquipped() ? Assembly.ENGINE.with(Assembly.stock(), grade) : Assembly.stock(); }
        String key() {
            return body.id() + "/" + type.id() + "/" + drive.layout() + "/" + drive.differential()
                + "/" + family + "/g" + grade + "/i" + induction;
        }
        String id() {
            return UUID.nameUUIDFromBytes(key().getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8);
        }
        String label() { return id() + " " + key(); }
    }

    private record SimState(VehicleDynamics.State road, ElectricDynamics.State electric,
                            MechanicalState mechanics, double peakSpeed) {}

    private static List<DriveConfig> driveConfigs() {
        var result = new ArrayList<DriveConfig>();
        for (var layout : DriveConfig.Layout.values()) for (var differential : DriveConfig.Differential.values())
            result.add(new DriveConfig(layout, differential, 50));
        return List.copyOf(result);
    }

    private static List<MatrixCase> cases() {
        var result = new ArrayList<MatrixCase>();
        for (var body : BodyStyle.values()) for (var type : Powertrain.values()) for (var drive : driveConfigs()) {
            if (!type.electric() || type.hybrid()) {
                for (var family : EngineFamily.values()) for (int grade = 1; grade <= 2; grade++) for (int induction = 0; induction <= 6; induction++)
                    result.add(new MatrixCase(body, type, drive, family, grade, induction));
            } else result.add(new MatrixCase(body, type, drive, EngineFamily.I4, 1, 0));
        }
        return List.copyOf(result);
    }

    private static VehicleDynamics.Setup setup(MatrixCase c, MechanicalState mechanics) {
        return new VehicleDynamics.Setup(c.config(), 6800, 3.7, c.family(), c.parts(),
            mechanics.coolantTemperature(), 1.1, mechanics, c.drive()).withPowertrain(c.type());
    }

    private static SimState advance(MatrixCase c, SimState state, VehicleDynamics.Input input, int steps) {
        var road = state.road();
        var electric = state.electric();
        var mechanics = state.mechanics();
        double peak = state.peakSpeed();
        for (int i = 0; i < steps; i++) {
            var setup = setup(c, mechanics);
            if (c.type().electric()) {
                var result = ElectricDynamics.step(c.type(), electric, ElectricDynamics.Mode.AUTO,
                    road.speed(), road.fuel(), road.engine(), road.wheels(), road.transmission(), true,
                    input, setup, GRIP, CONTACTS, TRAVEL, false, .0125, 20);
                road = result.road(); electric = result.electric(); mechanics = result.mechanics();
            } else road = VehicleDynamics.step(road, true, input, setup, .95, true, .0125);
            peak = Math.max(peak, road.groundSpeed());
        }
        return new SimState(road, electric, mechanics, peak);
    }

    private static void assertFinite(MatrixCase c, SimState state) {
        var s = state.road();
        String label = c.label();
        assertTrue(Double.isFinite(s.speed()) && Double.isFinite(s.rpm()) && Double.isFinite(s.fuel())
                && Double.isFinite(s.yawDelta()) && Double.isFinite(s.groundSpeed()),
            "Non-finite vehicle state: " + label + " state=" + s);
        assertTrue(s.fuel() >= 0, "Negative fuel: " + label);
        assertTrue(state.mechanics().validFor(slot -> PowertrainTopology.applicable(slot, c.type(), c.drive(), c.family())),
            "Invalid/duplicate mechanical state: " + label);
        assertTrue(Double.isFinite(state.mechanics().coolantTemperature()) && Double.isFinite(state.mechanics().oilTemperature()),
            "Non-finite mechanical temperature: " + label);
        if (c.type().electric()) {
            assertNotNull(state.electric(), "Missing electric state: " + label);
            assertTrue(Double.isFinite(state.electric().battery().energyJ()) && state.electric().battery().energyJ() >= 0,
                "Invalid battery energy: " + label);
            assertTrue(Double.isFinite(state.electric().motorC()) && Double.isFinite(state.electric().inverterC()),
                "Invalid electric temperature: " + label);
        }
    }

    @Test void allHighLevelCarModCombinationsComposeAndAdvanceTogether() throws Exception {
        var generated = cases();
        int passed = 0, engineCases = 0, electricOnlyCases = 0;
        long started = System.nanoTime();
        for (var c : generated) {
            assertEquals(c.body(), BodyStyle.byId(c.body().id()), "Body id round-trip: " + c.label());
            assertEquals(c.body(), BodyStyle.byNetworkId(c.body().networkId()), "Body network round-trip: " + c.label());
            assertFalse(c.body().hull(false, true, new double[4], 0).isEmpty(), "Empty body collision hull: " + c.label());
            assertTrue(c.body().length() > 3 && c.body().halfWidth() > .8 && c.body().roof() > 1,
                "Invalid body envelope: " + c.label());
            assertEquals(c.drive(), DriveConfig.decode(c.drive().packed(), c.drive().frontPercent()), "Drive config round-trip: " + c.label());

            var mechanics = PowertrainTopology.fresh(c.type(), c.drive(), c.family(), c.config(), c.parts());
            assertTrue(PowertrainTopology.availability(mechanics, c.type(), c.drive()) > .05,
                "Fresh configured car has no torque path: " + c.label());
            if (c.engineEquipped()) {
                assertTrue(EnginePart.ready(c.parts()), "Generated engine is incompatible: " + c.label());
                double torque = EngineBuild.torque(4500, c.family(), c.grade(), c.parts(), 6800, 90);
                assertTrue(Double.isFinite(torque) && torque > 0, "Generated engine has invalid torque: " + c.label());
                engineCases++;
            } else electricOnlyCases++;

            var state = new SimState(new VehicleDynamics.State(0, 0, 1, 40, 0, 0),
                c.type().electric() ? ElectricDynamics.State.initial(c.type(), .65) : null, mechanics, 0);
            double beforeFuel = state.road().fuel();
            double beforeBattery = c.type().electric() ? state.electric().battery().energyJ() : 0;
            double steer = ((passed % 5) - 2) * .03;
            state = advance(c, state, new VehicleDynamics.Input(.72, steer, false, false, false, false), 140);
            state = advance(c, state, new VehicleDynamics.Input(0, 0, true, false, false, false), 50);
            assertFinite(c, state);
            assertTrue(state.peakSpeed() > .15, "Generated car never moved: " + c.label());
            if (c.type().electric())
                assertTrue(state.electric().battery().energyJ() != beforeBattery || state.road().fuel() < beforeFuel,
                    "Electric/hybrid run consumed neither stored nor fuel energy: " + c.label());
            else assertTrue(state.road().fuel() < beforeFuel, "Combustion run consumed no fuel: " + c.label());
            passed++;
        }
        assertEquals(15_984, passed, "Unexpected generated high-level matrix size");
        assertEquals(15_876, engineCases, "Unexpected engine-equipped matrix size");
        assertEquals(108, electricOnlyCases, "Unexpected pure-EV matrix size");
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        Path out = Path.of("build/reports/generated-car-matrix.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, String.format(Locale.ROOT,
            "{\n  \"scope\": \"Full body x powertrain x layout x differential x engine-family x grade x induction composition; same simulation used by CarEntity\",\n  \"cases_passed\": %d,\n  \"engine_cases\": %d,\n  \"pure_ev_cases\": %d,\n  \"bodies\": %d,\n  \"powertrains\": %d,\n  \"drive_configs\": %d,\n  \"elapsed_ms\": %d\n}\n",
            passed, engineCases, electricOnlyCases, BodyStyle.values().length, Powertrain.values().length,
            driveConfigs().size(), elapsedMs));
    }

    @Test void everyAwdSplitAndDifferentialRoundTripsAndRoutesFreshTorque() {
        for (var differential : DriveConfig.Differential.values()) for (int split = 20; split <= 80; split++) {
            var drive = new DriveConfig(DriveConfig.Layout.AWD, differential, split);
            assertEquals(drive, DriveConfig.decode(drive.packed(), split));
            for (var type : Powertrain.values()) {
                var mechanics = PowertrainTopology.fresh(type, drive, EngineFamily.I4, Assembly.stock(), EnginePart.stock());
                assertTrue(PowertrainTopology.availability(mechanics, type, drive) > .05,
                    type + " " + differential + " split=" + split + " has no torque path");
            }
        }
    }

    @Test void deterministicStatefulFuzzKeepsMechanicalAndPhysicsInvariants() {
        var random = new Random(FUZZ_SEED);
        var generated = cases();
        for (int sequence = 0; sequence < 1000; sequence++) {
            var c = generated.get(random.nextInt(generated.size()));
            var mechanics = PowertrainTopology.fresh(c.type(), c.drive(), c.family(), c.config(), c.parts());
            var state = new SimState(new VehicleDynamics.State(0, 0, 1, 40, 0, 0),
                c.type().electric() ? ElectricDynamics.State.initial(c.type(), .55) : null, mechanics, 0);
            for (int action = 0; action < 32; action++) {
                String where = "seed=" + FUZZ_SEED + " sequence=" + sequence + " action=" + action + " case=" + c.label();
                switch (random.nextInt(6)) {
                    case 0 -> state = advance(c, state,
                        new VehicleDynamics.Input(random.nextDouble(), random.nextDouble(-.35, .35), false, random.nextInt(12) == 0, false, false), 4);
                    case 1 -> state = advance(c, state, new VehicleDynamics.Input(0, 0, true, false, false, random.nextBoolean()), 4);
                    case 2 -> {
                        var installed = PowertrainTopology.slots(c.type(), c.drive(), c.family()).stream()
                            .filter(slot -> state.mechanics().get(slot.key()) != null).toList();
                        if (!installed.isEmpty()) {
                            var slot = installed.get(random.nextInt(installed.size()));
                            var original = state.mechanics().get(slot.key());
                            var damaged = original.condition(Math.min(.8, original.wear() + random.nextDouble() * .08),
                                Math.min(.7, original.damage() + random.nextDouble() * .06), original.faults());
                            state = new SimState(state.road(), state.electric(), state.mechanics().with(slot.key(), damaged), state.peakSpeed());
                        }
                    }
                    case 3 -> {
                        var installed = PowertrainTopology.slots(c.type(), c.drive(), c.family()).stream()
                            .filter(slot -> state.mechanics().get(slot.key()) != null).toList();
                        if (!installed.isEmpty()) {
                            var slot = installed.get(random.nextInt(installed.size()));
                            var original = state.mechanics().get(slot.key());
                            var removed = state.mechanics().with(slot.key(), null);
                            assertNull(removed.get(slot.key()), "Removal failed " + where + " slot=" + slot.key());
                            var restored = removed.with(slot.key(), original);
                            assertEquals(original.id(), restored.get(slot.key()).id(), "Part identity changed " + where + " slot=" + slot.key());
                            state = new SimState(state.road(), state.electric(), restored, state.peakSpeed());
                        }
                    }
                    case 4 -> state = new SimState(state.road(), state.electric(),
                        state.mechanics().fluids(random.nextDouble() * 8, c.engineEquipped() ? random.nextDouble() * 5 : 0, random.nextDouble()), state.peakSpeed());
                    case 5 -> {
                        var roundTrip = PowertrainTopology.current(state.mechanics());
                        assertEquals(state.mechanics().parts(), roundTrip.parts(), "Current-version migration changed parts " + where);
                        state = new SimState(state.road(), state.electric(), roundTrip, state.peakSpeed());
                    }
                }
                try { assertFinite(c, state); }
                catch (AssertionError failure) { throw new AssertionError(where + "\n" + failure.getMessage(), failure); }
            }
        }
    }
}
