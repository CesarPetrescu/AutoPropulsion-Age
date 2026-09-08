package com.photonspark.sparkmotors.sim;

public final class TransmissionPhysics {
    public record State(int gear,int target,double remaining,double clutchHeat,double lateralSpeed,double yawRate) {
        public static State stopped(){return new State(1,1,0,20,0,0);}
    }
    public static State shift(State old,int desired,double dt){
        if(old.remaining>0){double time=Math.max(0,old.remaining-dt);return new State(time==0?old.target:old.gear,old.target,time,old.clutchHeat,old.lateralSpeed,old.yawRate);}
        if(desired!=old.gear)return new State(old.gear,desired,.24,old.clutchHeat,old.lateralSpeed,old.yawRate);
        return old;
    }
}
