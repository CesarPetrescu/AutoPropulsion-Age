package com.photonspark.sparkmotors.sim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BodyStyleTest {
    @Test void stableSaveAndNetworkIdsRoundTripAndUnknownValuesUseClassic(){
        assertEquals(6,BodyStyle.values().length);
        assertEquals(6,Arrays.stream(BodyStyle.values()).map(BodyStyle::id).distinct().count());
        for(var body:BodyStyle.values()){
            assertSame(body,BodyStyle.byId(body.id()));
            assertSame(body,BodyStyle.byNetworkId(body.networkId()));
        }
        assertSame(BodyStyle.CLASSIC_SEDAN,BodyStyle.byId(""));
        assertSame(BodyStyle.CLASSIC_SEDAN,BodyStyle.byId("future-unknown-body"));
        assertSame(BodyStyle.CLASSIC_SEDAN,BodyStyle.byId(null));
        assertSame(BodyStyle.CLASSIC_SEDAN,BodyStyle.byNetworkId(-1));
        assertSame(BodyStyle.CLASSIC_SEDAN,BodyStyle.byNetworkId(Integer.MAX_VALUE));
    }
    @Test void silhouettesAreDistinctAndTheWheelbaseIsNotChangedByCoachwork(){
        assertEquals(2.65,CarGeometry.FRONT_AXLE-CarGeometry.REAR_AXLE,1e-12);
        assertTrue(BodyStyle.HATCHBACK.length()<BodyStyle.CLASSIC_SEDAN.length());
        assertTrue(BodyStyle.SPORTS_CAR.roof()<BodyStyle.CLASSIC_SEDAN.roof());
        assertTrue(BodyStyle.SUV.roof()>BodyStyle.TOURING_SEDAN.roof());
        assertTrue(BodyStyle.VAN.roof()>BodyStyle.SUV.roof());
        assertTrue(BodyStyle.TOURING_SEDAN.length()>BodyStyle.CLASSIC_SEDAN.length());
        for(var body:BodyStyle.values()){
            assertTrue(body.halfWidth()>.83&&body.halfWidth()<1.1);
            assertTrue(body.nose()>CarGeometry.FRONT_AXLE+.5);
            assertTrue(body.tail()>-CarGeometry.REAR_AXLE+.5);
            assertTrue(body.roof()>body.belt());
            assertTrue(body.roofFront()>body.roofRear());
            assertTrue(body.cabinFront()>body.roofFront());
            assertTrue(body.cabinRear()<body.roofRear());
        }
    }
    @Test void originalHullRemainsExactlyTheLegacyGeometry(){
        for(boolean sport:new boolean[]{false,true})for(boolean wheels:new boolean[]{false,true}){
            var travel=new double[]{.02,-.04,.03,-.02};
            assertEquals(CarGeometry.hull(sport,wheels,travel,.4),BodyStyle.CLASSIC_SEDAN.hull(sport,wheels,travel,.4));
        }
    }
    @Test void allBodiesHaveFiniteClearHullsAndBlockRealObstacles(){
        for(var body:BodyStyle.values())for(boolean sport:new boolean[]{false,true}){
            var hull=body.hull(sport,true,new double[4],.3);
            assertTrue(VehicleCollision.clear(hull,java.util.List.of(),0,0,0,0),body.id());
            var wall=java.util.List.of(VehicleCollision.Box.bounds(-3,0,-.1,3,3,.1));
            assertFalse(VehicleCollision.clear(hull,wall,0,0,0,0),body.id());
            assertTrue(VehicleCollision.clear(hull,wall,0,0,10,Math.PI/4),body.id());
        }
    }
    @Test void cosmeticExhaustFitDoesNotMoveAnyPointInFrontOfTheRearAxle(){
        for(var body:BodyStyle.values()){
            for(double z:new double[]{CarGeometry.REAR_AXLE,-.5,0,1.35,2})assertEquals(z,body.exhaustZ(z),0);
            assertEquals(-body.tail()-.025,body.exhaustZ(-2.30),1e-10);
        }
    }
    @Test voidChargingAndSeatMountsRemainInsideTheirOwnBodyEnvelope(){
        for(var body:BodyStyle.values()){
            assertTrue(body.chargeX()>body.halfWidth());
            assertTrue(body.chargeX()<body.broadHalfWidth());
            assertTrue(body.chargeY()>.6&&body.chargeY()<body.roof());
            assertTrue(Math.abs(body.chargeZ())<body.tail());
            assertTrue(body.seatY()>=.1&&body.seatY()<body.roof()-.8);
            assertTrue(Math.abs(body.seatZ())<.5);
        }
    }
}
