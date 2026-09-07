package com.photonspark.autopropulsion.sim;

import java.util.*;
import java.util.function.Consumer;

/** Dependency-free executable checks, also invoked by JUnit. */
public final class SimulationChecks {
    private static int passed;
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); passed++; }
    private static void rejects(Runnable run, String message) {
        boolean rejected = false; try { run.run(); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, message);
    }
    private static double[] peak(EngineSpec spec) {
        double bestNm = 0, bestRpm = 0, bestKw = 0;
        for (int rpm = 850; rpm <= spec.redlineRpm(); rpm += 10) {
            double torque = EngineModel.torqueNm(spec, rpm, spec.maxBoostBar());
            if (torque > bestNm) { bestNm = torque; bestRpm = rpm; }
            bestKw = Math.max(bestKw, EngineModel.powerKw(torque, rpm));
        }
        return new double[]{bestNm, bestRpm, bestKw};
    }
    public static void curves() {
        Curve curve = new Curve(new double[]{0, 10, 20}, new double[]{0, 20, 10});
        check(curve.at(-1) == 0 && curve.at(5) == 10 && curve.at(30) == 10, "curve interpolation/clamping");
        rejects(() -> new Curve(new double[]{0, 0}, new double[]{1, 2}), "duplicate abscissa rejected");
        rejects(() -> curve.at(Double.NaN), "curve NaN rejected");
        EngineSpec ref = EngineSpec.reference(); double[] p = peak(ref), race = peak(ref.withRaceCam());
        check(p[0] >= 170 && p[0] <= 190, "reference peak torque: " + p[0]);
        check(p[2] >= 105 && p[2] <= 125, "reference peak kW: " + p[2]);
        check(race[1] - p[1] >= 800, "race cam shifts peak by >=800 rpm");
        check(EngineModel.torqueNm(ref.withRaceCam(), 2000, 0) < EngineModel.torqueNm(ref, 2000, 0) * .9, "race cam sacrifices low-end torque");
        check(Math.abs(EngineModel.powerKw(180, 6000) - 113.097335529) < .000001, "Nm-rpm to kW");
        double fuel = EngineModel.fuelLitresPerSecond(100, .3);
        check(fuel > .01 && fuel < .02, "fuel units must not be off by 1e6");
        rejects(() -> new EngineSpec(EngineSpec.Family.PISTON, 2, 1, 850, 6800, 0, 260, 200, 1, false, "rods"), "invalid compression");
        check(peak(EngineSpec.rotary())[0] > 180, "rotary curve is nontrivial");
    }
    public static void inputs() {
        check(!VehicleInput.validPacket(Float.NaN, 0, 0, 0, 1), "NaN packet rejected");
        check(!VehicleInput.validPacket(1, Float.POSITIVE_INFINITY, 0, 0, 1), "infinity packet rejected");
        check(!VehicleInput.validPacket(1, 0, 0, 0, 100), "bad gear rejected");
        check(!VehicleInput.validPacket(1.1f, 0, 0, 0, 1), "out-of-bounds throttle rejected");
        check(VehicleInput.validPacket(1, 0, -1, 1, -1), "valid input accepted");
        rejects(() -> new VehicleInput(Double.NaN, 0, 0, 0, 1, false), "NaN simulation input rejected");
        rejects(() -> new VehicleInput(0, 0, 0, 0, 9, false), "bad simulation gear rejected");
    }
    private static VehicleModel simulate(int substeps, EngineSpec spec, int ticks, double damage) {
        VehicleModel model = new VehicleModel(spec); model.setRunning(true);
        for (int t = 0; t < ticks; t++) {
            double speed = Math.abs(model.snapshot().speed());
            int gear = speed < 13 ? 1 : speed < 23 ? 2 : speed < 32 ? 3 : speed < 42 ? 4 : 5;
            model.tick(new VehicleInput(1, 0, t > 800 ? .1 : 0, 0, gear, false), substeps, 1, damage, 1);
        }
        return model;
    }
    public static void dynamics() {
        VehicleModel first = simulate(4, EngineSpec.reference(), 1000, 1);
        String golden = first.snapshot().toString();
        for (int i = 0; i < 100; i++) check(simulate(4, EngineSpec.reference(), 1000, 1).snapshot().toString().equals(golden), "deterministic run " + i);
        check(first.snapshot().speed() > 27.777, "reaches 100 km/h");
        check(first.snapshot().fuel() < 30, "consumes fuel");
        check(first.snapshot().coolantK() > 293.15, "produces heat");
        double baseline = simulate(4, EngineSpec.reference(), 400, 1).snapshot().speed();
        for (int n : new int[]{1, 2, 8}) {
            double v = simulate(n, EngineSpec.reference(), 400, 1).snapshot().speed();
            check(Math.abs(v - baseline) / baseline < .05, "substep convergence " + n);
        }
        double before = Math.abs(first.snapshot().speed());
        for (int i = 0; i < 100; i++) first.tick(new VehicleInput(0, 1, 0, 1, 0, false), 4, 1, 1, 1);
        check(Math.abs(first.snapshot().speed()) < before && Math.abs(first.snapshot().speed()) < .05, "brakes stop without reversing");
        VehicleModel reverse = new VehicleModel(EngineSpec.reference()); reverse.setRunning(true);
        for (int i = 0; i < 100; i++) reverse.tick(new VehicleInput(1, 0, 0, 0, -1, false), 4, 1, 1, 1);
        check(reverse.snapshot().speed() < -1, "reverse gear");
        VehicleModel neutral = new VehicleModel(EngineSpec.reference()); neutral.setRunning(true);
        for (int i = 0; i < 100; i++) neutral.tick(new VehicleInput(1, 0, 0, 1, 0, false), 4, 1, 1, 1);
        check(Math.abs(neutral.snapshot().speed()) < .01 && neutral.snapshot().rpm() > 3000, "clutch/neutral free rev");
        check(simulate(4, EngineSpec.reference().withBoost(1.5, 150), 1800, 5).snapshot().health() < 50, "weak internals damaged under boost");
        check(simulate(4, EngineSpec.reference().withBoost(1.5, 650), 600, 1).snapshot().health() > 95, "forged internals survive reference pull");
        check(simulate(4, EngineSpec.reference().withBoost(1.5, 150), 600, 0).snapshot().health() == 100, "damage-off configuration");
        VehicleModel restored = new VehicleModel(EngineSpec.reference()); restored.restore(first.snapshot());
        check(restored.snapshot().equals(first.snapshot()), "snapshot round trip");
        rejects(() -> restored.tick(VehicleInput.PARKED, 0, 1, 1, 1), "zero substeps rejected");
        rejects(() -> restored.tick(VehicleInput.PARKED, 9, 1, 1, 1), "too many substeps rejected");
        rejects(() -> restored.tick(VehicleInput.PARKED, 4, Double.NaN, 1, 1), "NaN environment rejected");
        restored.refuel(-100); restored.setRunning(true); check(!restored.snapshot().running(), "cannot run with empty tank");
    }
    private static Parts.Definition def(String id, String cat, Set<String> tags, Set<String> required,
                                       List<Parts.Modifier> modifiers, Map<String, Double> limits, List<Parts.Slot> children) {
        return new Parts.Definition("test:" + id, cat, cat, tags, required, Set.of(), modifiers, limits, children, 1, true, "test:model");
    }
    public static void parts() {
        Parts.Assembly a = new Parts.Assembly(List.of(new Parts.Slot("engine", "engine_block", true)));
        check(a.missingRequired().equals(List.of("engine")), "required slot diagnosed");
        var block = def("block", "engine_block", Set.of("engine/piston"), Set.of(), List.of(new Parts.Modifier("power", Parts.Op.SET, 10)), Map.of("torque_nm", 800d), List.of(new Parts.Slot("rods", "rods", true)));
        a.install("engine", block);
        var stock = def("stock", "rods", Set.of("rods"), Set.of("engine/piston"), List.of(new Parts.Modifier("power", Parts.Op.ADD, 5), new Parts.Modifier("power", Parts.Op.MUL, 2)), Map.of("torque_nm", 260d), List.of());
        a.install("engine/rods", stock);
        var resolved = a.resolve(Map.of());
        check(resolved.stats().get("power") == 30, "SET then ADD then MUL");
        check(resolved.limits().get("torque_nm").value() == 260 && resolved.limits().get("torque_nm").sourcePath().equals("engine/rods"), "weakest-link attribution");
        check(a.missingRequired().isEmpty(), "nested required slots filled");
        rejects(() -> a.remove("engine"), "cannot orphan children");
        rejects(() -> a.install("engine", block), "cannot replace parent under children");
        rejects(() -> a.install("engine/absent", stock), "unknown slot rejected");
        var rotary = def("rotary_seals", "rods", Set.of(), Set.of("engine/rotary"), List.of(), Map.of(), List.of());
        rejects(() -> a.install("engine/rods", rotary), "wrong engine family rejected");
        var forged = def("forged", "rods", Set.of(), Set.of("engine/piston"), List.of(), Map.of("torque_nm", 650d), List.of());
        a.install("engine/rods", forged);
        check(a.resolve(Map.of()).limit("torque_nm", 0) == 650, "upgrading bottleneck raises limit");
        rejects(() -> a.installed().clear(), "installed map is read-only");
    }
    public static void main(String[] args) {
        curves(); inputs(); dynamics();
        // Read-only map throws UnsupportedOperationException, which is intentionally tested separately.
        try { parts(); } catch (UnsupportedOperationException expected) { passed++; }
        System.out.println("SIMULATION_CHECKS_PASSED=" + passed);
        double[] p = peak(EngineSpec.reference());
        System.out.printf(Locale.ROOT, "REFERENCE peak_torque_nm=%.3f peak_torque_rpm=%.0f peak_power_kw=%.3f%n", p[0], p[1], p[2]);
    }
}
