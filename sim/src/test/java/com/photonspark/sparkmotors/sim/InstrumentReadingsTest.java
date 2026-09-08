package com.photonspark.sparkmotors.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstrumentReadingsTest {
    private MechanicalState fresh(){return MechanicalState.legacy(Assembly.stock(),EnginePart.stock(),90,90,100);}
    private InstrumentReadings read(MechanicalState m){return InstrumentReadings.read(m,20,4000,17,115,.4,.6,true);}
    @Test void gaugesUseDefinedSourcesAndWarnings(){var r=read(fresh());assertEquals(72,r.speed());assertEquals(4000,r.rpm());assertEquals(17,r.fuel());assertEquals(115,r.coolant());assertTrue(r.warnings().contains("HOT")&&r.warnings().contains("OIL"));assertTrue(r.fraction("rpm")>.4);}
    @Test void failedSenderDoesNotReportPerfectTemperatureOrPressure(){var r=read(fresh().with("cooling.sender",null).with("oil.sender",null));assertTrue(Double.isNaN(r.coolant())&&Double.isNaN(r.oilPressure()));assertTrue(r.warnings().contains("SENDER"));assertFalse(r.warnings().contains("HOT"));assertEquals(0,r.fraction("coolant"));}
    @Test void missingClusterOrPowerDisablesNeedles(){assertFalse(read(fresh().with("body.instruments",null)).powered());var m=fresh();m=m.with("electrical.battery",m.get("electrical.battery").operating(0,20));var r=read(m);assertFalse(r.powered());assertEquals(0,r.fraction("speed"));}
}
