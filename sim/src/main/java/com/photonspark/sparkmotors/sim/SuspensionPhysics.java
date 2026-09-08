package com.photonspark.sparkmotors.sim;

/** Unilateral spring/damper support. A tire can push the body up, never pull it to the road. */
public final class SuspensionPhysics {
    public static final double REACH=.28, REST_GAP=.14;
    public static double acceleration(double[] gaps,double verticalSpeed,MechanicalState m,DriveConfig drive){
        return acceleration(gaps,verticalSpeed,m,drive,VehicleDynamics.MASS);
    }
    public static double acceleration(double[] gaps,double verticalSpeed,MechanicalState m,DriveConfig drive,double mass){
        double support=0;
        for(int c=0;c<4;c++){
            if(!Double.isFinite(gaps[c])||gaps[c]>REACH||WheelDynamics.tireGrip(m,c)<=0)continue;
            double spring=m==null?1:m.capability("wheel."+ComponentSlot.CORNERS[c]+".spring");
            double damper=m==null?1:m.capability("wheel."+ComponentSlot.CORNERS[c]+".damper");
            double load=mass*9.81*(c<2?drive.frontWeight():1-drive.frontWeight())*.5;
            double stiffness=load/(REACH-REST_GAP)*(.3+.7*spring);
            support+=VehicleDynamics.clamp(stiffness*(REACH-gaps[c])-2100*damper*verticalSpeed,0,load*3.5);
        }
        return support/mass-9.81;
    }
}
