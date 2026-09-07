package com.photonspark.autopropulsion.sim;
import java.util.*;
import java.nio.file.*;

/** Dependency-free executable regression suite. Assertions are active without -ea. */
public final class SimulationTests {
    private static int passed;
    private static final List<String> names=new ArrayList<>();
    private static void check(boolean b,String message) { if(!b) throw new AssertionError(message); }
    private static void near(double a,double b,double eps) {check(Math.abs(a-b)<=eps,a+" != "+b);}
    private static void rejects(Runnable r) { try{r.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected rejection"); }
    private static void test(String name,Runnable r) {r.run();passed++;names.add(name);System.out.println("PASS "+name);}
    private static VehicleSimulation sim() {return new VehicleSimulation(EngineSpec.reference(),SimulationConfig.defaults());}
    private static double peakRpm(EngineSpec s) {double best=-1,r=0;for(int i=800;i<s.rpmLimit();i+=100){double t=EngineModel.sample(s,i,1,0).torqueNm();if(t>best){best=t;r=i;}}return r;}
    private static VehicleSimulation drive(SimulationConfig c,int ticks) {
        var v=new VehicleSimulation(EngineSpec.reference(),c);v.toggleEngine();v.shift(1);
        for(int i=0;i<ticks;i++){if(v.state().rpm()>5600&&v.state().gear()<5)v.shift(1);v.step(.05,new VehicleInput(1,0,.1,false,false),1);}
        return v;
    }
    public static void main(String[] args) throws Exception {
        test("curve interpolation and endpoint clamp",()->{var c=Curve.of(0,2,10,4);near(c.at(5),3,1e-12);near(c.at(-5),2,0);near(c.at(20),4,0);});
        test("curve rejects duplicate knots and NaN",()->{rejects(()->Curve.of(0,1,0,2));rejects(()->Curve.of(0,1,2,Double.NaN));});
        test("modifier production order",()->{var m=List.of(new StatResolver.Modifier("a","p",StatResolver.Op.ADD,10),new StatResolver.Modifier("b","p",StatResolver.Op.MUL,2),new StatResolver.Modifier("c","p",StatResolver.Op.MIN,45));near(StatResolver.evaluate(Map.of("p",20.),m).get("p"),45,0);});
        test("SET ordering deterministic",()->{var a=new StatResolver.Modifier("z","p",StatResolver.Op.SET,2);var b=new StatResolver.Modifier("a","p",StatResolver.Op.SET,1);check(StatResolver.evaluate(Map.of(),List.of(a,b)).equals(StatResolver.evaluate(Map.of(),List.of(b,a))),"order drift");});
        test("weakest structural part attribution",()->{var w=StatResolver.weakest(List.of(new StatResolver.Limit("rods",260),new StatResolver.Limit("block",800)));near(w.value(),260,0);check(w.source().equals("rods"),"wrong bottleneck");});
        test("modifier overflow rejected",()->rejects(()->StatResolver.evaluate(Map.of("p",Double.MAX_VALUE),List.of(new StatResolver.Modifier("a","p",StatResolver.Op.MUL,2)))));
        test("part installation and nested slot",()->{var t=tree();t.install("engine",engine());t.install("engine/rods",rod());check(t.installed().size()==2,"nested install");});
        test("incompatible install atomic",()->{var t=tree();rejects(()->t.install("engine",rod()));check(t.installed().isEmpty(),"partial mutation");});
        test("parent replacement rejects orphaning",()->{var t=tree();t.install("engine",engine());t.install("engine/rods",rod());rejects(()->t.install("engine",engine()));check(t.installed().size()==2,"lost child");});
        test("subtree removal complete",()->{var t=tree();t.install("engine",engine());t.install("engine/rods",rod());check(t.removeSubtree("engine").size()==2,"missing return");check(t.installed().isEmpty(),"orphan");});
        test("input clamping and hostile finite validation",()->{var i=new VehicleInput(5,-2,10,false,false);near(i.throttle(),1,0);near(i.brake(),0,0);near(i.steer(),1,0);rejects(()->new VehicleInput(Double.NaN,0,0,false,false));rejects(()->new VehicleInput(0,Double.POSITIVE_INFINITY,0,false,false));});
        test("reference dyno calibration",()->{double pt=0,pp=0;for(int r=800;r<6800;r+=100){var s=EngineModel.sample(EngineSpec.reference(),r,1,0);pt=Math.max(pt,s.torqueNm());pp=Math.max(pp,s.powerKw());}check(pt>=170&&pt<=190,"torque "+pt);check(pp>=105&&pp<=125,"power "+pp);});
        test("power identity",()->{var s=EngineModel.sample(EngineSpec.reference(),6000,1,0);near(s.powerKw(),s.torqueNm()*6000*2*Math.PI/60000,1e-12);});
        test("race cam peak shift and low rpm loss",()->{var a=EngineSpec.reference();var b=a.withUpgrades(true,0,false,false);check(peakRpm(b)-peakRpm(a)>=800,"peak not shifted");check(EngineModel.sample(b,2000,1,0).torqueNm()<.9*EngineModel.sample(a,2000,1,0).torqueNm(),"missing tradeoff");});
        test("boost raises torque",()->check(EngineModel.sample(EngineSpec.reference(),5000,1,1).torqueNm()>1.5*EngineModel.sample(EngineSpec.reference(),5000,1,0).torqueNm(),"boost ineffective"));
        test("fuel SI dimensional sanity",()->{double f=EngineModel.sample(EngineSpec.reference(),6000,1,0).fuelKgS();check(f>.005&&f<.015,"fuel units "+f);});
        test("limiter cuts production",()->near(EngineModel.sample(EngineSpec.reference(),7000,1,0).torqueNm(),0,0));
        test("neutral does not drive",()->{var v=sim();v.toggleEngine();for(int n=0;n<200;n++)v.step(.05,new VehicleInput(1,0,0,false,false),1);near(v.state().speedMs(),0,0);});
        test("car accelerates through gears",()->{var v=drive(SimulationConfig.defaults(),600);check(v.state().speedMs()>27.78,"never reached 100kmh: "+v.state().speedMs());check(v.state().gear()>1,"no shifts");});
        test("brakes stop without reversing",()->{var v=drive(SimulationConfig.defaults(),300);for(int n=0;n<200;n++)v.step(.05,VehicleInput.PARKED,1);near(v.state().speedMs(),0,.001);});
        test("reverse drives backwards",()->{var v=sim();v.toggleEngine();v.shift(-1);for(int n=0;n<100;n++)v.step(.05,new VehicleInput(1,0,0,false,false),1);check(v.state().speedMs()<0,"reverse sign");});
        test("clutch disengages drive",()->{var v=sim();v.toggleEngine();v.shift(1);for(int n=0;n<100;n++)v.step(.05,new VehicleInput(1,0,0,true,false),1);near(v.state().speedMs(),0,0);});
        test("100 repeated runs are bit-identical",()->{String ref=drive(SimulationConfig.defaults(),1000).state().toString();for(int n=0;n<100;n++)check(ref.equals(drive(SimulationConfig.defaults(),1000).state().toString()),"nondeterministic run "+n);});
        test("state save restore exact",()->{var a=drive(SimulationConfig.defaults(),100);var b=sim();b.restore(a.state());check(a.state().equals(b.state()),"state mismatch");});
        test("invalid persisted state rejected",()->{rejects(()->sim().restore(new VehicleSimulation.State(Double.NaN,0,0,0,293,293,0,100,0,false,"")));});
        test("fuel disabled config",()->near(drive(new SimulationConfig(4,0,1),300).state().fuelL(),45,0));
        test("zero fuel stops engine",()->{var v=sim();v.restore(new VehicleSimulation.State(0,900,0,0,293,293,0,100,0,true,""));v.toggleEngine();check(!v.state().running(),"started without fuel");});
        test("configuration bounds",()->{rejects(()->new SimulationConfig(0,1,1));rejects(()->new SimulationConfig(17,1,1));rejects(()->new SimulationConfig(4,-1,1));});
        test("substeps 1 through 16 remain finite",()->{for(int n:new int[]{1,2,4,8,16}){var s=drive(new SimulationConfig(n,1,1),2000).state();check(Double.isFinite(s.speedMs())&&s.coolantK()<600&&s.fuelL()>=0,"unstable config "+n);}});
        test("substep convergence 4 vs 8",()->near(drive(new SimulationConfig(4,1,1),400).state().speedMs(),drive(new SimulationConfig(8,1,1),400).state().speedMs(),2));
        test("negative and oversized time rejected",()->{rejects(()->sim().step(-1,VehicleInput.IDLE,1));rejects(()->sim().step(1,VehicleInput.IDLE,1));});
        test("structural damage attributed with boost",()->{var v=new VehicleSimulation(EngineSpec.reference().withUpgrades(false,1.5,false,false),SimulationConfig.defaults());v.toggleEngine();v.shift(1);for(int n=0;n<1500;n++){if(v.state().rpm()>5700&&v.state().gear()<5)v.shift(1);v.step(.05,new VehicleInput(1,0,0,false,false),1);}check(v.state().health()<95,"no rod damage");check(!v.state().fault().isEmpty(),"no diagnostic");});
        test("forged rods survive same boost better",()->{double[] h=new double[2];for(int j=0;j<2;j++){var v=new VehicleSimulation(EngineSpec.reference().withUpgrades(false,1.5,j==1,true),SimulationConfig.defaults());v.toggleEngine();v.shift(1);for(int n=0;n<800;n++){if(v.state().rpm()>5700&&v.state().gear()<5)v.shift(1);v.step(.05,new VehicleInput(1,0,0,false,false),1);}h[j]=v.state().health();}check(h[1]>h[0],"forged rods not helping");});
        test("damage multiplier zero",()->{var v=new VehicleSimulation(EngineSpec.reference().withUpgrades(false,1.5,false,false),new SimulationConfig(4,1,0));v.toggleEngine();v.shift(1);for(int n=0;n<500;n++)v.step(.05,new VehicleInput(1,0,0,false,false),1);near(v.state().health(),100,0);});
        test("long run bounded and odometer monotonic",()->{var v=sim();var random=new Random(42);v.toggleEngine();v.shift(1);double odo=0;for(int n=0;n<20000;n++){if(n%80==0)v.shift(random.nextBoolean()?1:-1);v.step(.05,new VehicleInput(random.nextDouble(),random.nextDouble()*.2,random.nextDouble()*2-1,false,false),.8);var s=v.state();check(s.odometerM()>=odo&&s.health()>=0&&Double.isFinite(s.oilK()),"state bounds");odo=s.odometerM();}});
        if(args.length>0) {
            Path p=Path.of(args[0]);Files.createDirectories(p.toAbsolutePath().getParent());
            String ns=names.stream().map(n->"\""+n+"\"").collect(java.util.stream.Collectors.joining(","));
            Files.writeString(p,"{\"passed\":"+passed+",\"failed\":0,\"tests\":["+ns+"]}\n");
        }
        System.out.println("RESULT "+passed+" passed, 0 failed");
    }
    private static PartTree tree(){return new PartTree(List.of(new PartTree.Slot("engine",Set.of("engine"))),Set.of("piston"));}
    private static PartTree.Part engine(){return new PartTree.Part("i4",Set.of("engine"),Set.of("piston"),Set.of(),List.of(new PartTree.Slot("rods",Set.of("rod"))));}
    private static PartTree.Part rod(){return new PartTree.Part("stock",Set.of("rod"),Set.of("engine"),Set.of(),List.of());}
}
