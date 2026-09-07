package com.photonspark.autopropulsion.sim;

import java.util.List;
import java.util.Set;

/** Regressions for mutation-before-validation defects found during local review. */
public final class SafetyTests {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var simulation = new VehicleSimulation(EngineSpec.reference(), SimulationConfig.defaults());
        var before = simulation.state();
        boolean rejected = false;
        try {
            simulation.restore(new VehicleSimulation.State(17, Double.NaN, 0, 45,
                293, 293, 0, 100, 0, false, ""));
        } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Non-finite state must be rejected");
        check(before.equals(simulation.state()), "Rejected restore changed live state");
        System.out.println("PASS rejected restore is atomic");

        var tree = new PartTree(List.of(new PartTree.Slot("provider", Set.of("engine")),
            new PartTree.Slot("consumer", Set.of("accessory"))), Set.of());
        var original = new PartTree.Part("original", Set.of("engine", "power"), Set.of(), Set.of(), List.of());
        tree.install("provider", original);
        tree.install("consumer", new PartTree.Part("dependent", Set.of("accessory"), Set.of("power"), Set.of(), List.of()));
        rejected = false;
        try {
            tree.install("provider", new PartTree.Part("replacement", Set.of("engine"), Set.of(), Set.of(), List.of()));
        } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Replacement broke another part's required tag");
        check(tree.installed().get("provider").equals(original), "Rejected replacement changed assembly");
        System.out.println("PASS replacement preserves dependent requirements");
        System.out.println("SAFETY RESULT 2 passed, 0 failed");
    }
    private SafetyTests() {}
}
