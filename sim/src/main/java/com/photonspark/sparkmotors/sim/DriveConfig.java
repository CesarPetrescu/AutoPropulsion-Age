package com.photonspark.sparkmotors.sim;

/** Chassis driveline routing and differential setup; independent of installed part condition. */
public record DriveConfig(Layout layout, Differential differential, int frontPercent) {
    public enum Layout { RWD, FWD, AWD }
    public enum Differential { OPEN, LIMITED_SLIP, LOCKED }
    public DriveConfig {
        layout=layout==null?Layout.RWD:layout;
        differential=differential==null?Differential.LIMITED_SLIP:differential;
        frontPercent=layout==Layout.FWD?100:layout==Layout.RWD?0:Math.clamp(frontPercent,20,80);
    }
    public static DriveConfig stock(){return preset(Layout.RWD);}
    public static DriveConfig preset(Layout layout){return new DriveConfig(layout,layout==Layout.FWD?Differential.OPEN:Differential.LIMITED_SLIP,40);}
    public int packed(){return layout.ordinal()|(differential.ordinal()<<2);}
    public static boolean valid(int packed,int split){return packed>=0&&(packed&~15)==0&&(packed&3)<3&&(packed>>2)<3&&split>=0&&split<=100;}
    public static DriveConfig decode(int packed,int split){return valid(packed,split)?new DriveConfig(Layout.values()[packed&3],Differential.values()[packed>>2],split):stock();}
    public double frontFraction(){return frontPercent/100.0;}
    public double frontWeight(){return switch(layout){case RWD->.52;case FWD->.60;case AWD->.55;};}
    public double efficiency(){return layout==Layout.AWD?.82:.88;}
    public double drivenOmega(WheelDynamics.State wheels){
        var c=wheels.corners();return (c.get(0).omega()+c.get(1).omega())*.5*frontFraction()+(c.get(2).omega()+c.get(3).omega())*.5*(1-frontFraction());
    }
    public double[] wheelTorques(double torque,WheelDynamics.State wheels){
        double[] result=new double[4];
        for(int axle=0;axle<2;axle++){
            int left=axle*2;double axleTorque=torque*(axle==0?frontFraction():1-frontFraction());
            if((axle==0&&frontPercent==0)||(axle==1&&frontPercent==100))continue;
            double lock=switch(differential){case OPEN->0;case LIMITED_SLIP->35;case LOCKED->200;};
            double capacity=differential==Differential.LOCKED?1800:Math.abs(axleTorque)*.35+25;
            double transfer=VehicleDynamics.clamp((wheels.corners().get(left).omega()-wheels.corners().get(left+1).omega())*lock,-capacity,capacity);
            result[left]=axleTorque*.5-transfer;result[left+1]=axleTorque*.5+transfer;
        }
        return result;
    }
}
