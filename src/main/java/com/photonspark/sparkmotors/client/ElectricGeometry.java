package com.photonspark.sparkmotors.client;

import com.mojang.blaze3d.vertex.*;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Authored hard-surface drive units in the original sedan's metre-scale coordinate system.
 * No replacement body: e-axle, finned inverter, sealed pack, coolant header and HV harness.
 */
public final class ElectricGeometry {
    private static final int ALLOY=0xFF9DA9B1,DARK=0xFF303A45,ORANGE=0xFFFF782D,TEAL=0xFF35BFB4;
    private static final net.minecraft.resources.ResourceLocation WHITE=AutoPropulsionAge.id("textures/entity/white.png");
    private ElectricGeometry(){}
    public static void render(CarEntity car,PoseStack poses,MultiBufferSource buffers,int light,boolean preview,boolean engineOnly){
        var type=car.powertrain();if(!type.electric())return;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        if(!engineOnly){
            double length=type.hybrid()?(type.plugIn()?1.45:.5):2.1;
            double center=-.1; // All packs stay between the axles; the compact HEV pack must not intersect the rear drive unit.
            // Hybrid modules leave a real center tunnel for gearbox, transfer case and shaft.
            if(type.hybrid())for(int side:new int[]{-1,1}){
                double left=side<0?-.66:.23,right=side<0?-.23:.66;
                box(out,poses,left,.25,center-length/2,right,.39,center+length/2,DARK,light);
                box(out,poses,left-.01,.27,center-length/2-.025,right+.01,.30,center+length/2+.025,ALLOY,light);
                for(int i=0;i<3;i++)box(out,poses,left+.04+i*.15,.30,center-length/2+.03,left+.06+i*.15,.40,center+length/2-.03,ALLOY,light);
            }else{
                box(out,poses,-.66,.25,center-length/2,.66,.39,center+length/2,DARK,light);
                box(out,poses,-.68,.27,center-length/2-.025,.68,.30,center+length/2+.025,ALLOY,light);
                for(int i=0;i<6;i++)box(out,poses,-.62+i*.21,.30,center-length/2+.03,-.60+i*.21,.40,center+length/2-.03,ALLOY,light);
            }
            if(type.plugIn()){
                var body=car.bodyStyle();double x=body.chargeX(),y=body.chargeY(),z=body.chargeZ();
                box(out,poses,x-.025,y-.08,z-.095,x-.001,y+.08,z+.095,DARK,light);
                box(out,poses,x,y-.04,z-.055,x+.017,y+.04,z+.055,car.plugged()?TEAL:ALLOY,light);
            }
        }
    }
    /** Cable is rendered in world-relative axes, independently of body pitch/roll. */
    public static void cable(CarEntity car,PoseStack poses,MultiBufferSource buffers,int light){
        if(!car.plugged())return;
        var body=car.bodyStyle();Vec3 start=new Vec3(body.chargeX()+.012,body.chargeY(),body.chargeZ()).yRot((float)-Math.toRadians(car.getYRot()));
        Vec3 end=Vec3.atCenterOf(car.chargerPosition()).subtract(car.position()).add(0,.15,0);
        if(start.distanceTo(end)>8)return;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));Vec3 old=start;
        for(int i=1;i<=24;i++){
            double t=i/24.0;Vec3 next=start.lerp(end,t).add(0,-.38*Math.sin(Math.PI*t),0);Vec3 delta=next.subtract(old);
            poses.pushPose();poses.translate(old.x,old.y,old.z);
            poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f((float)delta.x,(float)delta.y,(float)delta.z).normalize()));
            box(out,poses,-.025,0,-.025,.025,delta.length(),.025,i<3?ORANGE:0xFF20272E,light);poses.popPose();old=next;
        }
    }
    private static void box(VertexConsumer out,PoseStack poses,double x0,double y0,double z0,double x1,double y1,double z1,int color,int light){
        float[][] v={{(float)x0,(float)y0,(float)z0},{(float)x1,(float)y0,(float)z0},{(float)x1,(float)y1,(float)z0},{(float)x0,(float)y1,(float)z0},{(float)x0,(float)y0,(float)z1},{(float)x1,(float)y0,(float)z1},{(float)x1,(float)y1,(float)z1},{(float)x0,(float)y1,(float)z1}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
        float[][] normals={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};var pose=poses.last();
        for(int f=0;f<6;f++)for(int i:faces[f])out.addVertex(pose,v[i][0],v[i][1],v[i][2]).setColor(color).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,normals[f][0],normals[f][1],normals[f][2]);
    }
}
