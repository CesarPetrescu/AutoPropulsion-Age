package com.photonspark.sparkmotors.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.photonspark.sparkmotors.sim.VehicleCondition.Part;

/** Foot brake and handbrake are intentionally different controls. */
class HandbrakeTest {
    @Test void handbrakeSlowsTheCarButLessThanTheServiceBrake(){
        for(int grade=1;grade<=2;grade++)for(double grip:new double[]{.22,.7,1}){
            int config=Assembly.BRAKES.with(Assembly.stock(),grade);
            var setup=new VehicleDynamics.Setup(config,6800,3.7);
            var coast=VehicleDynamics.step(15,40,false,new VehicleDynamics.Input(0,0,false,false),setup,grip,true,.05);
            var foot=VehicleDynamics.step(15,40,false,new VehicleDynamics.Input(0,0,true,false),setup,grip,true,.05);
            var hand=VehicleDynamics.step(15,40,false,new VehicleDynamics.Input(0,0,false,false,true),setup,grip,true,.05);
            assertTrue(foot.speed()<hand.speed()&&hand.speed()<coast.speed());
            assertTrue(hand.slip()>0);
        }
    }
    @Test void destroyedBrakesCannotGenerateEitherBrakingForce(){
        var condition=new VehicleCondition();
        for(Part part:Part.values())if(part.assembly==Assembly.BRAKES)condition.damage(part,100);
        var setup=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,EnginePart.stock(),90,condition.effects(Assembly.stock(),EnginePart.stock()));
        var coast=VehicleDynamics.step(15,40,false,new VehicleDynamics.Input(0,0,false,false),setup,1,true,.05);
        var foot=VehicleDynamics.step(15,40,false,new VehicleDynamics.Input(0,0,true,false),setup,1,true,.05);
        var hand=VehicleDynamics.step(15,40,false,new VehicleDynamics.Input(0,0,false,false,true),setup,1,true,.05);
        assertEquals(coast.speed(),foot.speed(),1e-12);
        assertEquals(coast.speed(),hand.speed(),1e-12);
        assertEquals(0,hand.slip(),1e-12);
    }
}
