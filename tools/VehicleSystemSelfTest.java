import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sim.VehicleCondition.Part;
import java.util.*;
/** Dependency-free local smoke test; the Gradle JUnit and Minecraft GameTests are separate gates. */
public final class VehicleSystemSelfTest {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        int config=Assembly.stock(),parts=EnginePart.stock();
        var condition=new VehicleCondition();condition.impact(VehicleCondition.Zone.LEFT,14,config,parts);
        check(condition.health(Part.TIRE_FL)<100,"left corner damage");check(condition.health(Part.TIRE_FR)==100,"right corner must remain intact");
        condition.restore(Part.ENGINE_BLOCK,11,33);condition.repair(Part.TIRE_FL);check(condition.health(Part.ENGINE_BLOCK)==56,"selective repair");
        var wreck=new VehicleCondition();wreck.migrateLegacy(0,config,parts);check(!wreck.effects(config,parts).canRun(),"wreck migration");
        var parked=new VehicleCondition();for(int i=0;i<2000;i++)parked.step(config,parts,false,0,0,0,false,20,0,.05);check(parked.average(config,parts)==100,"parked wear");
        int audioCases=0;Set<String> sounds=new HashSet<>(VehicleAudio.events());check(sounds.size()==58,"catalog size");
        for(var family:EngineFamily.values())for(int grade=1;grade<=2;grade++)for(int exhaust=0;exhaust<=2;exhaust++)for(int induction=0;induction<=2;induction++)
        for(double rpm:new double[]{0,850,1800,2400,3000,4200,5500,7000,Double.NaN})for(double throttle:new double[]{0,.5,1})for(int damage:new int[]{0,35,90,100}){
            var c=new VehicleCondition();c.restore(Part.MUFFLER,0,damage);c.restore(Part.BRAKE_FL,0,damage);
            int conf=Assembly.EXHAUST.with(Assembly.ENGINE.with(config,grade),exhaust),ep=EnginePart.boosted(induction);
            var mix=VehicleAudio.mix(family,conf,ep,c,true,true,rpm,throttle,25,.6,.4,true);check(mix.size()<=8,"bounded sources");
            for(var voice:mix.values()){check(sounds.contains(voice.event()),"unregistered event");check(Float.isFinite(voice.pitch())&&voice.pitch()>=.5&&voice.pitch()<=2,"pitch");check(Float.isFinite(voice.volume())&&voice.volume()>=0&&voice.volume()<=1,"volume");}audioCases++;
        }
        int driveCases=0;
        for(var family:EngineFamily.values())for(int grade=1;grade<=2;grade++)for(int induction=0;induction<=2;induction++)for(var damaged:Part.values()){
            var c=new VehicleCondition();c.damage(damaged,90);int conf=Assembly.ENGINE.with(config,grade),ep=EnginePart.boosted(induction);
            var setup=new VehicleDynamics.Setup(conf,6800,3.7,family,ep,90,c.effects(conf,ep));
            VehicleDynamics.State state=new VehicleDynamics.State(0,0,1,40,0,0);
            for(int i=0;i<300;i++){state=VehicleDynamics.step(state.speed(),state.fuel(),true,new VehicleDynamics.Input(i<200?1:0,.1,i>=200,false),setup,1,true,.05);check(Double.isFinite(state.speed())&&Double.isFinite(state.rpm())&&state.fuel()>=0,"finite damaged dynamics");}driveCases++;
        }
        check(audioCases==13608,"audio matrix count");check(driveCases==1092,"driving matrix count");
        System.out.println("PASS: "+checks+" assertions; "+audioCases+" audio states; "+driveCases+" damaged drivetrain scenarios.");
    }
}
