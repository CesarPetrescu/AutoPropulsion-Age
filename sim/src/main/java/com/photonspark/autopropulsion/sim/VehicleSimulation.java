package com.photonspark.autopropulsion.sim;
import java.util.*;

/** Fixed-substep longitudinal drivetrain and bicycle steering; world contact belongs to the adapter. */
public final class VehicleSimulation {
    public static final double MASS_KG=1180, WHEEL_RADIUS_M=.31, WHEELBASE_M=2.55;
    private static final double[] RATIOS={3.2,0,3.4,2.1,1.4,1.0,.82};
    public record State(double speedMs,double rpm,int gear,double fuelL,double coolantK,double oilK,
                        double boostBar,double health,double odometerM,boolean running,String fault) {}
    private EngineSpec engine;
    private SimulationConfig config;
    private double speed,rpm,fuel=45,coolant=293.15,oil=293.15,boost,health=100,odometer,throttle;
    private boolean running;
    private int gear;
    private String fault="";
    public VehicleSimulation(EngineSpec engine,SimulationConfig config) { this.engine=Objects.requireNonNull(engine);this.config=Objects.requireNonNull(config); }
    public EngineSpec engine() { return engine; }
    public void configure(EngineSpec engine,SimulationConfig config) { this.engine=Objects.requireNonNull(engine);this.config=Objects.requireNonNull(config); }
    public State state() { return new State(speed,rpm,gear,fuel,coolant,oil,boost,health,odometer,running,fault); }
    public void restore(State s) {
        speed=Numbers.clamp(s.speedMs(),-80,80); rpm=Numbers.clamp(s.rpm(),0,20000); gear=Math.max(-1,Math.min(5,s.gear()));
        fuel=Numbers.clamp(s.fuelL(),0,60); coolant=Numbers.clamp(s.coolantK(),200,600);oil=Numbers.clamp(s.oilK(),200,600);
        boost=Numbers.clamp(s.boostBar(),0,3);health=Numbers.clamp(s.health(),0,100);odometer=Numbers.clamp(s.odometerM(),0,1e12);
        running=s.running()&&health>=10&&fuel>0;fault=s.fault()==null?"":s.fault().substring(0,Math.min(s.fault().length(),128));
        throttle=0;
    }
    public void toggleEngine() { if(running) running=false; else if(fuel>0&&health>=10){running=true;rpm=Math.max(rpm,900);} }
    public void stopEngine() { running=false; }
    public void shift(int direction) { gear=Math.max(-1,Math.min(5,gear+Integer.signum(direction))); }
    public void refuel(double litres) { fuel=Numbers.clamp(fuel+Numbers.positive(litres,"refuel"),0,60); }
    public void reconcileSpeed(double actualMs) { speed=Numbers.clamp(actualMs,-80,80); }
    public double yawRate(double steer) { return speed/WHEELBASE_M*Math.tan(Numbers.clamp(steer,-1,1)*.48)/(1+Math.abs(speed)*.035); }
    public void step(double tickSeconds,VehicleInput input,double grip) {
        if(Numbers.positive(tickSeconds,"dt")>.25) throw new IllegalArgumentException("tick exceeds 250ms");
        grip=Numbers.clamp(grip,.02,2); double dt=tickSeconds/config.substeps();
        for(int i=0;i<config.substeps();i++) substep(dt,input,grip);
    }
    private void substep(double dt,VehicleInput in,double grip) {
        throttle+=(in.throttle()-throttle)*(1-Math.exp(-dt/.12));
        double ratio=RATIOS[gear+1]*4.1, direction=gear<0?-1:1;
        double roadRpm=Math.abs(speed)/WHEEL_RADIUS_M*ratio*60/(2*Math.PI);
        double spool=Numbers.clamp((rpm-1800)/2400,0,1);
        double boostTarget=running?engine.maxBoostBar()*spool*throttle:0;
        boost+=(boostTarget-boost)*(1-Math.exp(-dt/(throttle<.15?.15:.7)));
        EngineModel.Sample full=EngineModel.sample(engine,rpm,1,boost);
        double driveForce=0;
        if(running&&fuel>0&&health>=10) {
            double available=full.torqueNm()*throttle*Math.max(.25,health/100);
            double idleControl=Numbers.clamp((920-rpm)*.4,0,140);
            double engagement=in.clutch()?0:Numbers.clamp(Math.abs(speed)/2+throttle*.8,0,1);
            double clutchTorque=gear==0?0:Numbers.clamp((rpm-roadRpm)*.18,-220,220)*engagement;
            double net=available+idleControl-(10+.002*rpm)-clutchTorque;
            rpm=Numbers.clamp(rpm+net/.32*60/(2*Math.PI)*dt,0,20000);
            if(rpm<300) {running=false;fault="P0335 STALLED";}
            driveForce=clutchTorque*ratio*.9/WHEEL_RADIUS_M*direction;
            double demand=EngineModel.sample(engine,rpm,Math.max(throttle,.08),boost).fuelKgS();
            fuel=Math.max(0,fuel-demand/.745*dt*config.fuelMultiplier());
            double heatW=demand*43_000_000*.25;
            double thermostat=Numbers.clamp((coolant-348)/10,.08,1);
            double coolingW=engine.radiatorWK()*(coolant-293.15)*(1+Math.sqrt(Math.abs(speed)))*thermostat;
            coolant=Numbers.clamp(coolant+(heatW-coolingW)*dt/(60_000+4*4180),200,600);
            double torqueOver=Math.max(0,full.torqueNm()*throttle-engine.torqueLimitNm())/engine.torqueLimitNm();
            double rpmOver=Math.max(0,rpm-engine.rpmLimit())/engine.rpmLimit();
            double tempOver=Math.max(0,coolant-410)/40;
            double damage=(torqueOver*torqueOver*6+rpmOver*rpmOver*50+tempOver*tempOver*5)*dt*config.damageMultiplier();
            health=Math.max(0,health-damage);
            if(torqueOver>0) fault="AP0001 "+engine.bottleneck();
            if(tempOver>0) fault="P0217 OVERHEAT";
            if(rpmOver>.08) fault="AP0002 OVERREV";
        } else {
            running=false;rpm=Math.max(0,rpm-1000*dt);
            coolant+=(293.15-coolant)*dt/300;
        }
        oil+=(coolant-oil)*dt/35;
        driveForce=Numbers.clamp(driveForce,-grip*MASS_KG*9.81*.65,grip*MASS_KG*9.81*.65);
        double drag=.5*1.225*.66*speed*Math.abs(speed);
        double unbraked=speed+(driveForce-drag)/MASS_KG*dt;
        double stopping=(.014*MASS_KG*9.81+in.brake()*9500*grip+(in.handbrake()?7000*grip:0))*dt/MASS_KG;
        speed=Math.copySign(Math.max(0,Math.abs(unbraked)-stopping),unbraked);
        speed=Numbers.clamp(speed,-80,80);
        odometer+=Math.abs(speed)*dt;
        if(fuel<=0) {running=false;fault="AP0003 NO FUEL";}
        if(health<10) {running=false;fault="AP0004 ENGINE FAILED";}
    }
}
