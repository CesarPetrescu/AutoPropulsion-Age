package com.photonspark.sparkmotors.sim.electric;
import org.junit.jupiter.api.Test;
final class ElectricTest {
    @Test void batteryEnergyAndTemperatureMatrix(){ElectricChecks.batteryConservation();}
    @Test void chargingLimitsVoltageAndEnergyMatrix(){ElectricChecks.chargers();}
    @Test void tractionRegenerationAndInterlocks(){ElectricChecks.driving();}
    @Test void seriesHybridGeneratorPaysForElectricity(){ElectricChecks.generator();}
}
