package com.photonspark.sparkmotors.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Cosmetic coachwork on the existing 2.65 m wheelbase. Dimensions are metres.
 * Stable IDs are persisted; body choice never modifies engine, battery or part condition.
 * Collision envelopes follow the shell, while tire size, axle mounts and tuning stay unchanged.
 */
public enum BodyStyle {
    CLASSIC_SEDAN(0,"classic_sedan","Classic Sedan",2.275,2.275,.970,1.040,1.510,.775,.255,-.950,-1.345,0,0),
    HATCHBACK(1,"hatchback","Hatchback",2.190,1.940,.955,1.060,1.610,.775,.270,-1.340,-1.800,.040,0),
    SPORTS_CAR(2,"sports_car","Sports Coupe",2.330,2.160,.980,1.040,1.390,.775,.180,-.780,-1.440,-.075,-.090),
    SUV(3,"suv","Utility SUV",2.275,2.270,.995,1.240,1.880,.775,.300,-1.430,-1.960,.200,0),
    VAN(4,"van","Panel Van",2.185,2.570,1.010,1.390,2.170,.775,.440,-2.100,-2.435,.330,.090),
    TOURING_SEDAN(5,"touring_sedan","Touring Sedan",2.310,2.420,.975,1.105,1.560,.775,.250,-.960,-1.460,.035,-.020);

    private final int networkId;
    private final String id,title;
    private final double nose,tail,halfWidth,belt,roof,cabinFront,roofFront,roofRear,cabinRear,cabinY,cabinZ;
    BodyStyle(int networkId,String id,String title,double nose,double tail,double halfWidth,
              double belt,double roof,double cabinFront,double roofFront,double roofRear,
              double cabinRear,double cabinY,double cabinZ) {
        this.networkId=networkId;this.id=id;this.title=title;this.nose=nose;this.tail=tail;
        this.halfWidth=halfWidth;this.belt=belt;this.roof=roof;this.cabinFront=cabinFront;
        this.roofFront=roofFront;this.roofRear=roofRear;this.cabinRear=cabinRear;
        this.cabinY=cabinY;this.cabinZ=cabinZ;
    }
    public int networkId(){return networkId;}
    public String id(){return id;}
    public String title(){return title;}
    public double nose(){return nose;}
    public double tail(){return tail;}
    public double halfWidth(){return halfWidth;}
    public double length(){return nose+tail;}
    public double roof(){return roof;}
    public double belt(){return belt;}
    public double cabinFront(){return cabinFront;}
    public double cabinRear(){return cabinRear;}
    public double roofFront(){return roofFront;}
    public double roofRear(){return roofRear;}
    public double cabinY(){return cabinY;}
    public double cabinZ(){return cabinZ;}
    public double seatY(){return .18+cabinY;}
    public double seatZ(){return .12+cabinZ;}
    public double broadHalfWidth(){return halfWidth+.17;}
    public double broadHalfLength(){return Math.max(nose,tail)+.10;}
    public static BodyStyle byId(String id){for(var body:values())if(body.id.equals(id))return body;return CLASSIC_SEDAN;}
    public static BodyStyle byNetworkId(int id){for(var body:values())if(body.networkId==id)return body;return CLASSIC_SEDAN;}

    /** Stretch only the exhaust behind the rear axle; never move the driven wheel mounts. */
    public double exhaustZ(double z){return z>=CarGeometry.REAR_AXLE?z:CarGeometry.REAR_AXLE+(z-CarGeometry.REAR_AXLE)*(tail+.025+CarGeometry.REAR_AXLE)/(2.30+CarGeometry.REAR_AXLE);}
    public double chargeX(){return halfWidth+.018;}
    public double chargeY(){return Math.max(.83,belt-.155);}
    public double chargeZ(){return -Math.min(tail-.31,1.73);}

    public List<VehicleCollision.Box> hull(boolean sport,boolean wheels,double[] travel,double steering){
        if(this==CLASSIC_SEDAN)return CarGeometry.hull(sport,wheels,travel,steering);
        var boxes=new ArrayList<VehicleCollision.Box>();
        double center=(nose-tail)*.5;
        boxes.add(VehicleCollision.Box.local(0,.22,center,.80,Math.min(belt,1.10),(nose+tail)*.5-.07));
        boxes.add(VehicleCollision.Box.local(0,.405,center,halfWidth+(sport?.025:0),belt,(nose+tail)*.5-.11));
        boxes.add(VehicleCollision.Box.local(0,.39,nose-.08,halfWidth-.025,.94,.08));
        boxes.add(VehicleCollision.Box.local(0,.43,-tail+.08,halfWidth-.025,belt-.03,.08));
        // The tall passenger/cargo envelope is separate from the low engine bay.
        boxes.add(VehicleCollision.Box.local(0,belt,(roofFront+roofRear)*.5,halfWidth-.09,roof,(roofFront-roofRear)*.5+.025));
        boxes.add(VehicleCollision.Box.local(0,belt,(cabinFront+cabinRear)*.5,halfWidth-.06,belt+(roof-belt)*.45,(cabinFront-cabinRear)*.5));
        for(double side:new double[]{-1,1})boxes.add(VehicleCollision.Box.local(side*(halfWidth+.065),belt+.005,.63,.072,belt+.13,.105));
        if(wheels)for(int c=0;c<4;c++)boxes.add(new VehicleCollision.Box(CarGeometry.wheelX(c),.18+travel[c],CarGeometry.wheelZ(c),.125,.676+travel[c],.336,c<2?steering:0));
        return List.copyOf(boxes);
    }
}
