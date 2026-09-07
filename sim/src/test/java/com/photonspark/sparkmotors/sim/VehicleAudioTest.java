package com.photonspark.sparkmotors.sim;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import static com.photonspark.sparkmotors.sim.VehicleCondition.Part.*;
class VehicleAudioTest {
    @Test void allFamiliesGradesExhaustsAndInductionsHaveFiniteBoundedVoices(){
        Set<String> registered=new HashSet<>(VehicleAudio.events());assertEquals(58,registered.size());int cases=0;
        for(var family:EngineFamily.values())for(int grade=1;grade<=2;grade++)for(int exhaust=0;exhaust<=2;exhaust++)for(int induction=0;induction<=2;induction++)
            for(double rpm:new double[]{0,850,1800,2400,3000,4200,5500,7000,Double.NaN})for(double throttle:new double[]{0,.5,1})for(int damage:new int[]{0,35,90,100}){
                var c=new VehicleCondition();c.restore(MUFFLER,0,damage);c.restore(BRAKE_FL,0,damage);
                int config=Assembly.EXHAUST.with(Assembly.ENGINE.with(Assembly.stock(),grade),exhaust),parts=EnginePart.boosted(induction);
                var mix=VehicleAudio.mix(family,config,parts,c,true,true,rpm,throttle,25,.6,.4,true);
                assertTrue(mix.size()<=8);
                for(var voice:mix.values()){
                    assertTrue(registered.contains(voice.event()));assertTrue(Float.isFinite(voice.pitch())&&voice.pitch()>=.5&&voice.pitch()<=2);
                    assertTrue(Float.isFinite(voice.volume())&&voice.volume()>=0&&voice.volume()<=1);
                }cases++;
            }
        assertEquals(13608,cases);
    }
    private Map<String,VehicleAudio.Voice> mix(int config,int parts,VehicleCondition c,boolean on,boolean ground,double throttle){return VehicleAudio.mix(EngineFamily.I4,config,parts,c,on,ground,3000,throttle,20,.6,.8,true);}
    @Test void mufflerChangesTimbreAndVolumeIncludingFailure(){
        var c=new VehicleCondition();var stock=mix(Assembly.stock(),EnginePart.stock(),c,true,true,1);
        var sport=mix(Assembly.EXHAUST.with(Assembly.stock(),2),EnginePart.stock(),c,true,true,1);
        var open=mix(Assembly.EXHAUST.with(Assembly.stock(),0),EnginePart.stock(),c,true,true,1);
        assertTrue(stock.get("low").event().contains("stock"));assertTrue(sport.get("low").event().contains("sport"));assertTrue(open.get("low").event().contains("open"));
        assertTrue(stock.get("low").volume()<sport.get("low").volume());assertTrue(sport.get("low").volume()<open.get("low").volume());
        c.damage(MUFFLER,100);assertTrue(mix(Assembly.stock(),EnginePart.stock(),c,true,true,1).get("low").event().contains("open"));
    }
    @Test void inductionLayersAreExclusiveAndGradeAffectsIntake(){
        var c=new VehicleCondition();assertFalse(mix(Assembly.stock(),EnginePart.stock(),c,true,true,1).containsKey("induction"));
        assertEquals("turbo",mix(Assembly.stock(),EnginePart.boosted(1),c,true,true,1).get("induction").event());
        assertEquals("supercharger",mix(Assembly.stock(),EnginePart.boosted(2),c,true,true,1).get("induction").event());
        assertTrue(mix(Assembly.ENGINE.with(Assembly.stock(),2),EnginePart.stock(),c,true,true,1).get("intake").volume()>mix(Assembly.stock(),EnginePart.stock(),c,true,true,1).get("intake").volume());
    }
    @Test void offEngineCanCoastButAirborneTiresAreSilent(){
        var c=new VehicleCondition();var coast=mix(Assembly.stock(),EnginePart.stock(),c,false,true,0);
        assertFalse(coast.containsKey("low"));assertTrue(coast.containsKey("roll"));
        var air=mix(Assembly.stock(),EnginePart.stock(),c,true,false,1);assertTrue(air.containsKey("low"));assertFalse(air.containsKey("roll"));assertFalse(air.containsKey("brake"));
        assertTrue(VehicleAudio.mix(EngineFamily.I4,Assembly.stock(),EnginePart.stock(),c,false,true,0,0,0,0,0,false).isEmpty());
    }
    @Test void failedBrakesGrindAndFailedEngineStopsCombustion(){
        var c=new VehicleCondition();for(var p:new VehicleCondition.Part[]{BRAKE_FL,BRAKE_FR,BRAKE_RL,BRAKE_RR})c.damage(p,100);
        assertEquals("brake_grind",mix(Assembly.stock(),EnginePart.stock(),c,true,true,1).get("brake").event());
        c.damage(INTERNALS,100);assertFalse(mix(Assembly.stock(),EnginePart.stock(),c,true,true,1).containsKey("low"));
    }
}
