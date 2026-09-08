package com.photonspark.sparkmotors.sim;

import java.util.*;
import java.util.regex.Pattern;

/** Shared metre-scale rig coordinates: local +Z is the nose, -X is the left side. */
public final class CarGeometry {
    public static final double HALF_TRACK=.83, FRONT_AXLE=1.35, REAR_AXLE=-1.30, AXLE_HEIGHT=.34;
    private static final Pattern CORNER=Pattern.compile("(?:^|_)(fl|fr|rl|rr)(?:_|$)");
    private CarGeometry(){}
    public static int corner(String name){var m=CORNER.matcher(name);return m.find()?Arrays.asList(ComponentSlot.CORNERS).indexOf(m.group(1)):-1;}
    public static double wheelX(int corner){return corner%2==0?-HALF_TRACK:HALF_TRACK;}
    public static double wheelZ(int corner){return corner<2?FRONT_AXLE:REAR_AXLE;}
    public static boolean spins(String name){return name.contains("tire_")||name.contains("rim_")||name.startsWith("brake_disc_")||name.startsWith("hub_");}
    public static boolean steers(String name){return spins(name)||name.startsWith("brake_caliper_")||name.startsWith("brake_pad_")||name.startsWith("knuckle_");}
    /** Body-to-world transform shared by the road integration and collision response. */
    public static double worldX(double forward,double lateral,double yaw){return -Math.sin(yaw)*forward-Math.cos(yaw)*lateral;}
    public static double worldZ(double forward,double lateral,double yaw){return Math.cos(yaw)*forward-Math.sin(yaw)*lateral;}
    public static double forward(double x,double z,double yaw){return -Math.sin(yaw)*x+Math.cos(yaw)*z;}
    public static double lateral(double x,double z,double yaw){return -Math.cos(yaw)*x-Math.sin(yaw)*z;}
    /** Limit cosmetic shell tilt to the space the actual four suspension positions permit. */
    public static double tiltFraction(double pitch,double roll,double[] travel){
        double lo=0,hi=1;
        for(int i=0;i<12;i++){
            double f=(lo+hi)*.5,p=Math.toRadians(pitch*f),r=Math.toRadians(roll*f);boolean fits=true;
            for(int c=0;c<4;c++){
                double relativeY=-Math.sin(r)*wheelX(c)+Math.cos(r)*(Math.cos(p)*(AXLE_HEIGHT+travel[c])-Math.sin(p)*wheelZ(c));
                if(relativeY-AXLE_HEIGHT>SuspensionPhysics.MAX_BUMP)fits=false;
            }
            if(fits)lo=f;else hi=f;
        }
        return lo;
    }
    /** Shape proxies fit the shell, bumpers, cabin, mirrors and tires, not their enclosing square. */
    public static List<VehicleCollision.Box> hull(boolean sport,boolean wheels,double[] travel,double steering){
        var boxes=new ArrayList<VehicleCollision.Box>();
        boxes.add(VehicleCollision.Box.local(0,.22,0,.82,1.035,2.15)); // floor / engine and luggage bays
        boxes.add(VehicleCollision.Box.local(0,.35,-.16,sport?1.025:.97,1.04,1.93)); // sills / closed doors / fenders
        boxes.add(VehicleCollision.Box.local(0,.435,2.18,.94,.995,.095));
        boxes.add(VehicleCollision.Box.local(0,.47,-2.18,.94,.995,.095));
        boxes.add(VehicleCollision.Box.local(0,1.04,-.285,.83,1.51,1.06)); // cabin only, leaving space above bonnet
        for(double side:new double[]{-1,1})boxes.add(VehicleCollision.Box.local(side*1.045,1.054,.61,.065,1.146,.095));
        if(sport)boxes.add(VehicleCollision.Box.local(0,.4365,2.2,.99,.4635,.165));
        if(wheels)for(int c=0;c<4;c++){
            double t=travel[c];
            // The lower rubber contact is handled by the suspension rays. The solid hub/tread
            // envelope catches walls/ceilings without turning the tire into a second hard spring.
            boxes.add(new VehicleCollision.Box(wheelX(c),.18+t,wheelZ(c),.125,.676+t,.336,c<2?steering:0));
        }
        return List.copyOf(boxes);
    }
}
