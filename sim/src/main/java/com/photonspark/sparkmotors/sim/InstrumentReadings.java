package com.photonspark.sparkmotors.sim;

import java.util.*;

/** Normal instruments respect sender/power failures. The workshop can explicitly request assisted telemetry. */
public record InstrumentReadings(boolean powered,double speed,double rpm,double fuel,double coolant,double oilPressure,double voltage,double boost,double odometer,List<String> warnings) {
    public static InstrumentReadings read(MechanicalState m,double speed,double rpm,double fuel,double coolant,double oilPressure,double boost,boolean running){
        boolean power=m.capability("body.instruments")>.2&&m.capability("electrical.fuse")>.2&&m.capability("electrical.wiring")>.2&&CircuitPhysics.batteryCharge(m)>.1;
        var warnings=new ArrayList<String>();double voltage=CircuitPhysics.measure(m,rpm,running,speed).voltage();
        double temperature=m.capability("cooling.sender")>.2?coolant:Double.NaN,pressure=m.capability("oil.sender")>.2?oilPressure:Double.NaN;
        if(!Double.isFinite(temperature)||!Double.isFinite(pressure))warnings.add("SENDER");
        if(Double.isFinite(temperature)&&temperature>110)warnings.add("HOT");if(running&&Double.isFinite(pressure)&&pressure<.65)warnings.add("OIL");
        if(running&&voltage<13)warnings.add("CHARGE");if(m.brakeFluid()<.2)warnings.add("BRAKE");if(fuel<5)warnings.add("FUEL");
        return new InstrumentReadings(power,Math.abs(speed)*3.6,rpm,fuel,temperature,pressure,voltage,boost,m.distance()/1000,List.copyOf(warnings));
    }
    public double fraction(String gauge){if(!powered)return 0;return switch(gauge){case "speed"->VehicleDynamics.clamp(speed/240,0,1);case "rpm"->VehicleDynamics.clamp(rpm/8000,0,1);case "fuel"->fuel/50;case "coolant"->VehicleDynamics.clamp((coolant-40)/100,0,1);case "oil"->VehicleDynamics.clamp(oilPressure/7,0,1);case "voltage"->VehicleDynamics.clamp((voltage-8)/8,0,1);default->0;};}
}
