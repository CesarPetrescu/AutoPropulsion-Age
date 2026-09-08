package com.photonspark.sparkmotors.sim;

import java.util.*;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.*;

/** Four independent contacts. No contact means no chassis braking or tire-road sound at that corner. */
public final class WheelDynamics {
    public record Corner(double omega,double angle,double travel,boolean contact,double slip,double brakeForce,double temperature) {
        public static Corner stopped(){return new Corner(0,0,0,false,0,0,20);}
    }
    public record State(List<Corner> corners){public State{corners=List.copyOf(corners);if(corners.size()!=4)throw new IllegalArgumentException("Four corners required");}public static State stopped(){return new State(Collections.nCopies(4,Corner.stopped()));}}
    public record Forces(State state,double braking,double rolling,double yawMoment,double driveGrip) {}
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
    public static Forces step(State previous,MechanicalState m,int config,Input in,double speed,double driveForce,double grip,boolean[] contacts,double[] travels,double dt){
        var result=new ArrayList<Corner>();double brakes=0,rolling=0,moment=0,driveGrip=0;
        for(int c=0;c<4;c++){
            var old=previous.corners.get(c);boolean contact=contacts[c]&&Assembly.WHEELS.variant(config)>0;
            double traction=tireGrip(m,c)*grip*(Assembly.WHEELS.variant(config)==2?1.17:1);
            double spring=m==null?1:m.capability("wheel."+ComponentSlot.CORNERS[c]+".spring");
            double damper=m==null?1:m.capability("wheel."+ComponentSlot.CORNERS[c]+".damper");
            double load=clamp(.25*(.35+.65*spring)+travels[c]*.4+(travels[c]-old.travel)/Math.max(.001,dt)*damper*.003,.04,.42);
            double limit=MASS*9.81*load*traction;
            double service=in.brake()&&Assembly.BRAKES.variant(config)>0?MASS*(Assembly.BRAKES.variant(config)==2?10.8:8)*(c<2?.30:.20)*brakeCapability(m,c,true):0;
            double hand=in.handbrake()&&c>=2&&Assembly.BRAKES.variant(config)>0?MASS*3*brakeCapability(m,c,false):0;
            double fade=clamp(1-(old.temperature-350)/500,.15,1);
            double drag=0;if(m!=null){var caliper=m.get("wheel."+ComponentSlot.CORNERS[c]+".caliper");if(caliper!=null&&(caliper.faults()&PartInstance.SEIZED)!=0)drag=1000;}
            double request=Math.max(service*fade,hand)+drag;
            double wheelForce=c>=2?driveForce*.5:0;
            // A locked wheel balances opposing engine/brake torques before the tire contact limit.
            double brake=contact?Math.min(request,Math.abs(speed)<.1?Math.max(limit,Math.abs(wheelForce)):limit):0;brakes+=brake;moment+=(c%2==0?-1:1)*brake*.83;
            if(contact){rolling+=35+(1-traction)*140+(m==null?0:(1-m.capability("wheel."+ComponentSlot.CORNERS[c]+".bearing"))*220);if(c>=2)driveGrip+=traction*.5;}
            double omega=old.omega;
            if(contact){double slip=clamp((Math.abs(wheelForce)+request)/Math.max(100,limit)-1,0,2);omega=speed/WHEEL_RADIUS*(1+(wheelForce>request?slip:-Math.min(1,slip)));}
            else{omega+=(wheelForce*WHEEL_RADIUS-Math.signum(omega)*Math.min(Math.abs(omega)*1.5/dt,request*WHEEL_RADIUS))*dt/1.5;}
            omega=clamp(omega,-300,300);double travel=old.travel+(travels[c]-(1-spring)*.12-old.travel)*(1-Math.exp(-dt*(3+damper*9)));
            double temperature=old.temperature+(brake*Math.abs(speed)/1000/8-(old.temperature-20)*(.016+Math.abs(speed)*.002))*dt;
            double slip=contact?Math.abs(omega*WHEEL_RADIUS-speed)/Math.max(1,Math.abs(speed)):0;
            result.add(new Corner(omega,old.angle+omega*dt,travel,contact,slip,brake,clamp(temperature,20,1000)));
        }
        return new Forces(new State(result),brakes,rolling,moment,driveGrip);
    }
    public static MechanicalState wear(MechanicalState m,State wheels,double speed,double dt){
        for(int c=0;c<4;c++){
            var w=wheels.corners.get(c);String k="wheel."+ComponentSlot.CORNERS[c]+".";var pad=m.get(k+"pad");
            if(pad!=null)m=m.with(k+"pad",pad.condition(pad.wear()+w.brakeForce*Math.abs(speed)*dt/2e9,pad.damage(),pad.faults()).operating(pad.reserve(),w.temperature));
            var disc=m.get(k+"disc");if(disc!=null)m=m.with(k+"disc",disc.operating(disc.reserve(),w.temperature));
            var tire=m.get(k+"tire");if(tire!=null&&w.contact)m=m.with(k+"tire",tire.condition(tire.wear()+w.slip*Math.abs(speed)*dt*.000005,tire.damage(),tire.faults()).operating(tire.reserve(),20+Math.abs(speed)*1.2+w.slip*30));
        }return m;
    }
}
