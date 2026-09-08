package com.photonspark.sparkmotors.sim;

public final class TransmissionPhysics {
    public record State(int gear,int target,double remaining,double clutchHeat,double lateralSpeed,double yawRate,double steering,double longitudinalAcceleration,double lateralAcceleration) {
        public State(int gear,int target,double remaining,double clutchHeat,double lateralSpeed,double yawRate){this(gear,target,remaining,clutchHeat,lateralSpeed,yawRate,0,0,0);}
        public static State stopped(){return new State(1,1,0,20,0,0);}
        public State heat(double value){return new State(gear,target,remaining,value,lateralSpeed,yawRate,steering,longitudinalAcceleration,lateralAcceleration);}
        public State motion(double lateral,double yaw,double steer,double ax,double ay){return new State(gear,target,remaining,clutchHeat,lateral,yaw,steer,ax,ay);}
    }
    public static State shift(State old,int desired,double dt){
        if(old.remaining>0){double time=Math.max(0,old.remaining-dt);return new State(time==0?old.target:old.gear,old.target,time,old.clutchHeat,old.lateralSpeed,old.yawRate,old.steering,old.longitudinalAcceleration,old.lateralAcceleration);}
        if(desired!=old.gear)return new State(old.gear,desired,.24,old.clutchHeat,old.lateralSpeed,old.yawRate,old.steering,old.longitudinalAcceleration,old.lateralAcceleration);
        return old;
    }
}
