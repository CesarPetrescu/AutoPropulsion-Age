package com.photonspark.autopropulsion.sim;

import org.junit.jupiter.api.Test;

final class SimulationTest {
    @Test void calibratedCurvesAndUnits() { SimulationChecks.curves(); }
    @Test void rejectUntrustedInputs() { SimulationChecks.inputs(); }
    @Test void deterministicPhysicsAndConfigurationMatrix() { SimulationChecks.dynamics(); }
    @Test void partCompatibilityAndWeakestLink() {
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, SimulationChecks::parts);
    }
}
