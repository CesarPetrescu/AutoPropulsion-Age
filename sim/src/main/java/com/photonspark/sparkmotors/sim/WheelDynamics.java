package com.photonspark.sparkmotors.sim;

import java.util.*;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.*;

/** Four independent contacts. No contact means no chassis braking or tire-road sound at that corner. */
public final class WheelDynamics {
    public static final double INERTIA=1.8;
    public record Corner(double omega,double angle,double travel,boolean contact,double slip,double brakeForce,double temperature) {
        public static Corner stopped(){return new Corner(0,0,0,false,0,0,20);}
    }
    public record State(List<Corner> corners,boolean initialized){public State(List<Corner> corners){this(corners,true);}public State{corners=List.copyOf(corners);if(corners.size()!=4)throw new IllegalArgumentException("Four corners required");}public static State stopped(){return new State(Collections.nCopies(4,Corner.stopped()),false);}}
    public record Forces(State state,double braking,double rolling,double yawMoment,double driveGrip,double forward,double lateral,double holdingGrip) {}
    public static State restoreTemperatures(State previous,MechanicalState m){
        var corners=new ArrayList<Corner>();
        for(int c=0;c<4;c++){var old=previous.corners.get(c);var disc=m.get("wheel."+ComponentSlot.CORNERS[c]+".disc");corners.add(new Corner(old.omega,old.angle,old.travel,old.contact,old.slip,old.brakeForce,disc==null?20:disc.temperature()));}
        return new State(corners);
    }
    public static double tireGrip(MechanicalState m,int c){
        if(m==null)return 1;String prefix="wheel."+ComponentSlot.CORNERS[c]+".";var tire=m.get(prefix+"tire");if(tire==null)return 0;
        double pressure=clamp(tire.reserve()/2.3,.06,1.05);return pressure*(1-tire.wear()*.65)*(1-tire.damage()*.85)*m.capability(prefix+"rim");
    }
    public static double brakeCapability(MechanicalState m,int c,boolean hydraulic){
        if(m==null)return 1;String k="wheel."+ComponentSlot.CORNERS[c]+".";
        var pad=m.get(k+"pad");double pads=pad==null?0:Math.max(.05,1-pad.wear())*pad.capability();
        double value=pads*m.capability(k+"disc")*m.capability(k+"caliper");
        return hydraulic?value*m.capability(k+"brake_hose")*clamp(m.brakeFluid()/.35,0,1):value;
    }
    /** Compatibility entry point for direct component/brake tests. */
    public static Forces step(State previous,MechanicalState m,int config,Input in,double speed,double driveForce,double grip,boolean[] contacts,double[] travels,double dt){
        return step(previous,new Setup(config,6800,3.7,EngineFamily.I4,EnginePart.stock(),90,1.4,m),in,speed,0,0,in.steer()*.45,driveForce*WHEEL_RADIUS,new double[]{grip,grip,grip,grip},contacts,travels,0,0,dt);
    }
    public static Forces step(State previous,Setup setup,Input in,double speed,double lateral,double yawRate,double steering,double driveTorque,double[] grip,boolean[] contacts,double[] travels,double ax,double ay,double dt){
        var result=new ArrayList<Corner>();var m=setup.mechanics();int config=setup.config();
        double brakes=0,rolling=0,moment=0,forward=0,sideways=0,driveGrip=0,holding=0;
        double[] torques=setup.drive().wheelTorques(driveTorque,previous,dt);
        double front=setup.drive().frontWeight(),longTransfer=clamp(ax,-15,15)*CG_HEIGHT/(WHEELBASE*9.81);
        for(int c=0;c<4;c++){
            var old=previous.corners.get(c);boolean contact=contacts[c]&&Assembly.WHEELS.variant(config)>0&&tireGrip(m,c)>0;
            double x=c<2?WHEELBASE*(1-front):-WHEELBASE*front,y=c%2==0?TRACK/2:-TRACK/2;
            double link=m==null?1:m.capability("wheel."+ComponentSlot.CORNERS[c]+".link");
            double angle=c<2?steering*link:0;
            double ca=Math.cos(angle),sa=Math.sin(angle),pointU=speed-yawRate*y,pointV=lateral+yawRate*x;
            double u=pointU*ca+pointV*sa,v=-pointU*sa+pointV*ca;
            double spring=m==null?1:m.capability("wheel."+ComponentSlot.CORNERS[c]+".spring");
            double damper=m==null?1:m.capability("wheel."+ComponentSlot.CORNERS[c]+".damper");
            double axle=clamp(c<2?front-longTransfer:1-front+longTransfer,0,1);
            double lateralTransfer=clamp(clamp(ay,-15,15)*CG_HEIGHT/(TRACK*9.81)*(c<2?front:1-front),-axle*.5,axle*.5);
            // Unloading an inside wheel redistributes the existing axle load;
            // clamping each corner independently would invent extra normal force.
            double fraction=axle*.5+(c%2==0?-1:1)*lateralTransfer;
            double load=MASS*9.81*fraction*(.30+.70*spring);
            // Suspension load variation is bounded separately from longitudinal/lateral transfer.
            load*=clamp(1+(travels[c]-old.travel)*damper*.25/Math.max(.005,dt),.7,1.3);
            double mu=clamp(grip[c],0,2)*tireGrip(m,c)*(Assembly.WHEELS.variant(config)==2?1.17:1);
            double service=in.brake()&&Assembly.BRAKES.variant(config)>0?MASS*(Assembly.BRAKES.variant(config)==2?10.8:8)*(c<2?.30:.20)*brakeCapability(m,c,true):0;
            double hand=in.handbrake()&&c>=2&&Assembly.BRAKES.variant(config)>0?MASS*3*brakeCapability(m,c,false):0;
            double fade=clamp(1-(old.temperature-350)/500,.15,1),dragBrake=0;
            if(m!=null){var caliper=m.get("wheel."+ComponentSlot.CORNERS[c]+".caliper");if(caliper!=null&&(caliper.faults()&PartInstance.SEIZED)!=0)dragBrake=1000;}
            double request=Math.max(service*fade,hand)+dragBrake,brakeTorque=request*WHEEL_RADIUS;
            double omega=previous.initialized?old.omega:u/WHEEL_RADIUS;
            double free=omega+torques[c]*dt/INERTIA;
            double braked=free-clamp(free,-brakeTorque*dt/INERTIA,brakeTorque*dt/INERTIA);
            double fx=0,fy=0,alpha=Math.atan2(v,Math.max(2.5,Math.abs(u))),limit=mu*load;
            if(contact){
                // Implicit longitudinal slip response prevents stiff wheel oscillation at 80 Hz.
                double stiffness=load*11/Math.max(3,Math.abs(u));
                fx=stiffness*(braked*WHEEL_RADIUS-u)/(1+dt*stiffness*(WHEEL_RADIUS*WHEEL_RADIUS/INERTIA+4/MASS));
                fy=-limit*Math.sin(1.35*Math.atan(9*alpha));
                double predictedSlip=Math.abs(braked*WHEEL_RADIUS-u)/Math.max(3,Math.abs(u));
                double sliding=1-.22*clamp((predictedSlip-.18)/1.2,0,1);
                limit*=sliding;
                // Acceleration, braking and cornering share the same finite contact patch.
                double combined=Math.hypot(fx,fy),scale=combined>limit?limit/Math.max(.001,combined):1;
                fx*=scale;fy*=scale;
                double rollingForce=35+(1-tireGrip(m,c))*140+(m==null?0:(1-m.capability("wheel."+ComponentSlot.CORNERS[c]+".bearing"))*220);
                double rollingU=clamp(u*MASS/(4*dt),-rollingForce,rollingForce);
                // Rolling resistance is also contact-limited (zero friction means zero road force).
                rollingU=clamp(rollingU,-Math.max(0,limit-Math.hypot(fx,fy)),Math.max(0,limit-Math.hypot(fx,fy)));
                fx-=rollingU;rolling+=Math.abs(rollingU);
                double worldU=fx*ca-fy*sa,worldV=fx*sa+fy*ca;
                forward+=worldU;sideways+=worldV;moment+=x*worldV-y*worldU;
                driveGrip+=mu*(c<2?setup.drive().frontFraction()*.5:(1-setup.drive().frontFraction())*.5);
            }
            double torque=torques[c]-fx*WHEEL_RADIUS;
            if(contact&&braked==0&&brakeTorque>Math.abs(torques[c]))holding+=Math.min(limit,(brakeTorque-Math.abs(torques[c]))/WHEEL_RADIUS);
            double beforeBrake=omega+torque*dt/INERTIA;
            omega=beforeBrake-clamp(beforeBrake,-brakeTorque*dt/INERTIA,brakeTorque*dt/INERTIA);
            if(!contact)omega*=Math.exp(-dt*.015);
            omega=clamp(omega,-450,450);
            double brake=contact?Math.min(request,limit):0;brakes+=brake;
            double travel=old.travel+(travels[c]-(1-spring)*.12-old.travel)*(1-Math.exp(-dt*(3+damper*9)));
            double temperature=old.temperature+(brake*Math.abs(u)/1000/8-(old.temperature-20)*(.016+Math.abs(u)*.002))*dt;
            double slip=contact?Math.hypot((omega*WHEEL_RADIUS-u)/Math.max(3,Math.abs(u)),Math.tan(alpha)):0;
            result.add(new Corner(omega,old.angle+omega*dt,travel,contact,clamp(slip,0,4),brake,clamp(temperature,20,1000)));
        }
        return new Forces(new State(result),brakes,rolling,moment,driveGrip,forward,sideways,holding);
    }
    public static MechanicalState wear(MechanicalState m,State wheels,double speed,double dt){
        for(int c=0;c<4;c++){
            var w=wheels.corners.get(c);String k="wheel."+ComponentSlot.CORNERS[c]+".";var pad=m.get(k+"pad");
            if(pad!=null)m=m.with(k+"pad",pad.condition(pad.wear()+w.brakeForce*Math.abs(speed)*dt/2e9,pad.damage(),pad.faults()).operating(pad.reserve(),w.temperature));
            var disc=m.get(k+"disc");if(disc!=null)m=m.with(k+"disc",disc.operating(disc.reserve(),w.temperature));
            var tire=m.get(k+"tire");if(tire!=null){double target=20+(w.contact?Math.abs(speed)*1.2+w.slip*30:0);double temperature=tire.temperature()+(target-tire.temperature())*(1-Math.exp(-dt*.03));m=m.with(k+"tire",tire.condition(tire.wear()+(w.contact?w.slip*Math.abs(speed)*dt*.000005:0),tire.damage(),tire.faults()).operating(tire.reserve(),temperature));}
        }return m;
    }
}
