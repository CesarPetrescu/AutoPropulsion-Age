package com.photonspark.sparkmotors.sim;

import java.util.*;

/** Swept oriented boxes against actual voxel boxes. Yaw is tested along its arc;
 * translation uses continuous separating-axis intervals, so thin walls cannot be skipped.
 * Obstacles are static during a substep. SI units, world X/Y/Z, positive Minecraft yaw.
 */
public final class VehicleCollision {
    private static final double EPS=1e-7, SKIN=1e-5;
    private VehicleCollision(){}
    public record Box(double x,double minY,double z,double halfX,double maxY,double halfZ,double yaw){
        public static Box local(double x,double minY,double z,double halfX,double maxY,double halfZ){return new Box(x,minY,z,halfX,maxY,halfZ,0);}
        public static Box bounds(double x0,double y0,double z0,double x1,double y1,double z1){return local((x0+x1)/2,y0,(z0+z1)/2,(x1-x0)/2,y1,(z1-z0)/2);}
        public Box at(double px,double py,double pz,double angle){double c=Math.cos(angle),s=Math.sin(angle);return new Box(px+c*x-s*z,py+minY,pz+s*x+c*z,halfX,py+maxY,halfZ,yaw+angle);}
        public double extentX(){return Math.abs(Math.cos(yaw))*halfX+Math.abs(Math.sin(yaw))*halfZ;}
        public double extentZ(){return Math.abs(Math.sin(yaw))*halfX+Math.abs(Math.cos(yaw))*halfZ;}
    }
    public record Hit(double fraction,double nx,double ny,double nz,double x,double y,double z){}
    public record Result(double x,double y,double z,double yaw,double vx,double vy,double vz,double yawRate,boolean horizontal,boolean vertical,double impact,Hit contact){}
    private static double radius(Box b,double nx,double ny,double nz){
        double c=Math.cos(b.yaw),s=Math.sin(b.yaw);
        return b.halfX*Math.abs(nx*c+nz*s)+b.halfZ*Math.abs(-nx*s+nz*c)+(b.maxY-b.minY)*.5*Math.abs(ny);
    }
    private static double[][] axes(Box a,Box b){double ac=Math.cos(a.yaw),as=Math.sin(a.yaw),bc=Math.cos(b.yaw),bs=Math.sin(b.yaw);return new double[][]{{0,1,0},{ac,0,as},{-as,0,ac},{bc,0,bs},{-bs,0,bc}};}
    public static boolean overlaps(Box a,Box b){
        if(a.maxY<=b.minY+EPS||b.maxY<=a.minY+EPS||Math.abs(a.x-b.x)>=a.extentX()+b.extentX()-EPS||Math.abs(a.z-b.z)>=a.extentZ()+b.extentZ()-EPS)return false;
        for(double[] n:axes(a,b))if(Math.abs((a.x-b.x)*n[0]+((a.minY+a.maxY-b.minY-b.maxY)*.5)*n[1]+(a.z-b.z)*n[2])>=radius(a,n[0],n[1],n[2])+radius(b,n[0],n[1],n[2])-EPS)return false;
        return true;
    }
    /** Earliest contact for fixed orientation; null means the whole segment is clear. */
    public static Hit sweep(Box a,Box b,double dx,double dy,double dz){
        double enter=-Double.MAX_VALUE,exit=Double.MAX_VALUE,nx=0,ny=0,nz=0;
        for(double[] n:axes(a,b)){
            double distance=(a.x-b.x)*n[0]+(a.minY+a.maxY-b.minY-b.maxY)*.5*n[1]+(a.z-b.z)*n[2];
            double r=radius(a,n[0],n[1],n[2])+radius(b,n[0],n[1],n[2]),v=dx*n[0]+dy*n[1]+dz*n[2];
            if(Math.abs(v)<EPS){if(Math.abs(distance)>=r-EPS)return null;continue;}
            double t0=(-r-distance)/v,t1=(r-distance)/v;
            if(t0>t1){double temp=t0;t0=t1;t1=temp;}
            if(t0>enter){enter=t0;double sign=v>0?-1:1;nx=n[0]*sign;ny=n[1]*sign;nz=n[2]*sign;}
            exit=Math.min(exit,t1);if(enter>exit+EPS)return null;
        }
        if(enter< -EPS||enter>1||exit<0||dx*nx+dy*ny+dz*nz>=-EPS)return null;
        enter=Math.max(0,enter);
        // Support face midpoint for square-on contact, support corner for oblique contact.
        double c=Math.cos(a.yaw),s=Math.sin(a.yaw),sx=Math.signum(-nx*c-nz*s),sz=Math.signum(nx*s-nz*c);
        if(Math.abs(nx*c+nz*s)<EPS)sx=0;if(Math.abs(-nx*s+nz*c)<EPS)sz=0;
        double px=a.x+dx*enter+c*sx*a.halfX-s*sz*a.halfZ,pz=a.z+dz*enter+s*sx*a.halfX+c*sz*a.halfZ;
        // A short obstacle contacts only the overlapping portion of a longer vehicle face.
        double bc=Math.cos(b.yaw),bs=Math.sin(b.yaw),rx=px-b.x,rz=pz-b.z;
        double bx=VehicleDynamics.clamp(rx*bc+rz*bs,-b.halfX,b.halfX),bz=VehicleDynamics.clamp(-rx*bs+rz*bc,-b.halfZ,b.halfZ);
        px=b.x+bc*bx-bs*bz;pz=b.z+bs*bx+bc*bz;
        return new Hit(enter,nx,ny,nz,px,(a.minY+a.maxY)*.5+dy*enter,pz);
    }
    public static boolean clear(List<Box> hull,List<Box> obstacles,double x,double y,double z,double yaw){
        for(Box local:hull){Box box=local.at(x,y,z,yaw);for(Box obstacle:obstacles)if(overlaps(box,obstacle))return false;}return true;
    }
    public static Result move(List<Box> hull,List<Box> obstacles,double x,double y,double z,double yaw,double deltaYaw,double vx,double vy,double vz,double yawRate,double mass,double dt){
        double accepted=yaw;boolean horizontal=false,vertical=false;
        // Sample at <= 0.5 degree (under 2.2 cm at the bumper) and bisect first contact.
        int samples=Math.max(1,(int)Math.ceil(Math.abs(deltaYaw)/Math.toRadians(.5)));
        for(int i=1;i<=samples;i++){
            double target=yaw+deltaYaw*i/samples;
            if(!clear(hull,obstacles,x,y,z,target)){
                double lo=accepted,hi=target;for(int j=0;j<12;j++){double mid=(lo+hi)*.5;if(clear(hull,obstacles,x,y,z,mid))lo=mid;else hi=mid;}
                accepted=lo;yawRate=0;horizontal=true;break;
            }accepted=target;
        }
        yaw=accepted;
        double dx=vx*dt,dy=vy*dt,dz=vz*dt,impact=0;Hit strongest=null;
        for(int iteration=0;iteration<6;iteration++){
            Hit first=null;
            for(Box local:hull){Box box=local.at(x,y,z,yaw);for(Box obstacle:obstacles){Hit hit=sweep(box,obstacle,dx,dy,dz);if(hit!=null&&(first==null||hit.fraction<first.fraction))first=hit;}}
            if(first==null){x+=dx;y+=dy;z+=dz;break;}
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz),t=Math.max(0,first.fraction-SKIN/Math.max(SKIN,length));
            x+=dx*t;y+=dy*t;z+=dz*t;
            if(Math.abs(first.ny)>.5){vertical=true;vy=0;}
            else{
                horizontal=true;
                double rx=first.x-x,rz=first.z-z,cross=rx*first.nz-rz*first.nx;
                double normal=vx*first.nx+vz*first.nz+yawRate*cross;
                if(normal<0){
                    double inertia=VehicleDynamics.YAW_INERTIA*mass/VehicleDynamics.MASS;
                    double impulse=-normal/(1/mass+cross*cross/inertia);
                    vx+=impulse*first.nx/mass;vz+=impulse*first.nz/mass;yawRate+=impulse*cross/inertia;
                    // Passive wall friction; never adds energy or erases tangential momentum.
                    double tx=-first.nz,tz=first.nx,tc=rx*tz-rz*tx,tangent=vx*tx+vz*tz+yawRate*tc;
                    double friction=VehicleDynamics.clamp(-tangent/(1/mass+tc*tc/inertia),-.12*impulse,.12*impulse);
                    vx+=friction*tx/mass;vz+=friction*tz/mass;yawRate+=friction*tc/inertia;
                    if(-normal>impact){impact=-normal;strongest=first;}
                }
            }
            double remaining=1-t;dx*=remaining;dy*=remaining;dz*=remaining;
            double into=dx*first.nx+dy*first.ny+dz*first.nz;
            if(into<0){dx-=into*first.nx;dy-=into*first.ny;dz-=into*first.nz;}
            if(dx*dx+dy*dy+dz*dz<EPS*EPS)break;
        }
        return new Result(x,y,z,yaw,vx,vy,vz,VehicleDynamics.clamp(yawRate,-5,5),horizontal,vertical,impact,strongest);
    }
}
