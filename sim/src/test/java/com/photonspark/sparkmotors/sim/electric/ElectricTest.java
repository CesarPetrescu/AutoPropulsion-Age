package com.photonspark.sparkmotors.sim.electric;
import org.junit.jupiter.api.Test;
final class ElectricTest {
    @Test void batteryEnergyAndTemperatureMatrix(){ElectricChecks.batteryConservation();}
    @Test void chargingLimitsVoltageAndEnergyMatrix(){ElectricChecks.chargers();}
    @Test void tractionRegenerationAndInterlocks(){ElectricChecks.driving();}
    @Test void seriesHybridGeneratorPaysForElectricity(){ElectricChecks.generator();}
    @Test void chargeTelemetryFaultPriorityAndOneThermalClock(){
        var type=Powertrain.ELECTRIC_400;var b=BatteryModel.State.initial(type.battery,.25);
        var t=ChargingModel.Tier.RAPID;
        var r=ChargingModel.step(type,b,t,480,3000,.8,true,false,0,.05,-20);
        org.junit.jupiter.api.Assertions.assertTrue(r.terminalJ()<0&&r.currentA()<0&&r.voltageV()>0);
        org.junit.jupiter.api.Assertions.assertEquals(r.terminalJ(),r.currentA()*r.voltageV()*.05,1e-6);
        org.junit.jupiter.api.Assertions.assertEquals(r.inputJ(),r.storedJ()+r.lossJ()+r.heaterJ(),1e-6);
        org.junit.jupiter.api.Assertions.assertTrue(r.battery().temperatureC()>=b.temperatureC(),"charger adds heat; vehicle owns passive cooling");
        var bad=ChargingModel.step(type,b,t,560,0,.8,true,false,0,.05,20);
        org.junit.jupiter.api.Assertions.assertEquals(ChargingModel.Status.OVERVOLTAGE,bad.status());
    }
}
