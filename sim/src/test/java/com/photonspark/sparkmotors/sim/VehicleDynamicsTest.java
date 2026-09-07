package com.photonspark.sparkmotors.sim;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VehicleDynamicsTest {
    private final VehicleDynamics.Setup stock = new VehicleDynamics.Setup(Assembly.stock(), 6800, 3.7);
    private VehicleDynamics.State drive(VehicleDynamics.Setup setup, boolean reverse) {
        VehicleDynamics.State s = new VehicleDynamics.State(0, 0, 1, 40, 0, 0);
        for (int i=0; i<800; i++) s=VehicleDynamics.step(s.speed(),s.fuel(),true,new VehicleDynamics.Input(1,0,false,reverse),setup,1,true,.0125);
        return s;
    }
    @Test void referenceEngine() {
        assertEquals(180,PistonEngine.torque(4500,false,6800),.001);
        assertTrue(PistonEngine.powerKw(6000,false,6800)>105);
        assertEquals(0,PistonEngine.torque(7000,true,6800));
    }
    @Test void reachesRoadSpeedAndUsesFuel() {
        var s=drive(stock,false);assertTrue(s.speed()>20);assertTrue(s.speed()<40);assertTrue(s.gear()>1);assertTrue(s.fuel()<40);
    }
    @Test void sportEngineActuallyChangesAcceleration() {
        var sport=new VehicleDynamics.Setup(Assembly.ENGINE.with(Assembly.stock(),2),6800,3.7);
        assertTrue(drive(sport,false).speed()>drive(stock,false).speed()+1);
    }
    @Test void reverseAndBrakes() {
        assertTrue(drive(stock,true).speed()< -5);
        double speed=25;
        for(int i=0;i<100;i++) speed=VehicleDynamics.step(speed,40,true,new VehicleDynamics.Input(0,0,true,false),stock,1,true,.05).speed();
        assertEquals(0,speed,.01);
    }
    @Test void missingAssembliesAndEmptyTankPreventDriving() {
        var missing=new VehicleDynamics.Setup(Assembly.TRANSMISSION.with(Assembly.stock(),0),6800,3.7);
        assertEquals(0,drive(missing,false).speed(),.001);
        assertEquals(0,VehicleDynamics.step(0,0,true,new VehicleDynamics.Input(1,0,false,false),stock,1,true,.05).speed());
    }
    @Test void deterministicAndFinite() {
        assertEquals(drive(stock,false),drive(stock,false));
        var s=VehicleDynamics.step(Double.NaN,Double.NaN,true,new VehicleDynamics.Input(Double.NaN,Double.POSITIVE_INFINITY,false,false),stock,1,true,.05);
        assertTrue(Double.isFinite(s.speed()));assertTrue(Double.isFinite(s.fuel()));
    }
    @Test void configEncodingDoesNotChangeOtherSlots() {
        int c=Assembly.BRAKES.with(Assembly.stock(),2);
        for(var a:Assembly.values()) assertEquals(a==Assembly.BRAKES?2:1,a.variant(c));
        assertThrows(IllegalArgumentException.class,()->Assembly.BRAKES.with(c,3));
    }
    @Test void suspensionChangesSteeringResponse() {
        var input=new VehicleDynamics.Input(0,.5,false,false);
        var sport=new VehicleDynamics.Setup(Assembly.SUSPENSION.with(Assembly.stock(),2),6800,3.7);
        assertTrue(VehicleDynamics.step(10,40,true,input,sport,1,true,.05).yawDelta()>
            VehicleDynamics.step(10,40,true,input,stock,1,true,.05).yawDelta());
    }
}
