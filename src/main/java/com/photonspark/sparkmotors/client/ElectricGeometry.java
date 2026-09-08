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
            double center=type.hybrid()&&!type.plugIn()?-1.5:-.1;
            box(out,poses,-.66,.25,center-length/2,.66,.39,center+length/2,DARK,light);
            box(out,poses,-.68,.27,center-length/2-.025,.68,.30,center+length/2+.025,ALLOY,light);
            for(int i=0;i<6;i++)box(out,poses,-.62+i*.21,.30,center-length/2+.03,-.60+i*.21,.40,center+length/2-.03,ALLOY,light);
            // Sealed service disconnect and orange sill conduit.
            box(out,poses,.48,.40,center+.1,.59,.46,center+.24,ORANGE,light);
            box(out,poses,.60,.42,-1.55,.64,.46,1.42,ORANGE,light);
            // Rear e-axle on hybrids, front e-axle on BEVs; single-speed reduction is integral.
            if(type.hybrid())driveUnit(out,poses,0,.40,-1.25,.72,light);
            box(out,poses,.963,.72,-1.53,.987,.88,-1.34,DARK,light);
            box(out,poses,.988,.76,-1.49,1.005,.84,-1.38,car.plugged()?TEAL:ALLOY,light);
        }
        if(!type.hybrid()&&(preview||car.hoodProgress>.05||engineOnly)){
            driveUnit(out,poses,0,.63,1.36,1,light);
            box(out,poses,-.35,.81,1.07,.35,.94,1.61,ALLOY,light);
            for(int i=0;i<9;i++)box(out,poses,-.32+i*.08,.94,1.09,-.30+i*.08,.985,1.59,DARK,light);
            box(out,poses,-.52,.69,1.18,-.40,.90,1.52,DARK,light);
            box(out,poses,-.515,.895,1.20,-.405,.91,1.31,TEAL,light);
            box(out,poses,.46,.65,1.16,.65,.86,1.44,0xFFCBD9DB,light);
            box(out,poses,.50,.86,1.24,.60,.90,1.36,DARK,light);
            box(out,poses,.34,.84,1.31,.59,.88,1.37,ORANGE,light);
            box(out,poses,.59,.44,1.31,.64,.88,1.37,ORANGE,light);
            box(out,poses,-.24,.805,1.615,.25,.90,1.645,type.battery.nominalV()>400?ORANGE:TEAL,light);
        }
        if(type.hybrid()&&(preview||car.hoodProgress>.05||engineOnly)){
            // Compact generator on the outboard side of the existing engine assembly.
            box(out,poses,.43,.52,1.12,.68,.72,1.41,ALLOY,light);
            for(int i=0;i<4;i++)box(out,poses,.44+i*.06,.51,1.13,.46+i*.06,.73,1.40,DARK,light);
            box(out,poses,.60,.72,1.26,.65,.81,1.42,ORANGE,light);
        }
    }
    private static void driveUnit(VertexConsumer out,PoseStack p,double x,double y,double z,double scale,int light){
        box(out,p,x-.43*scale,y-.13*scale,z-.22*scale,x+.29*scale,y+.13*scale,z+.22*scale,ALLOY,light);
        box(out,p,x+.29*scale,y-.15*scale,z-.24*scale,x+.49*scale,y+.16*scale,z+.24*scale,DARK,light);
        for(int i=0;i<7;i++)box(out,p,x-.40*scale+i*.095*scale,y+.13*scale,z-.21*scale,x-.37*scale+i*.095*scale,y+.16*scale,z+.21*scale,DARK,light);
        box(out,p,x-.78,y-.035,z-.035,x+.78,y+.035,z+.035,ALLOY,light);
    }
    /** Cable is rendered in world-relative axes, independently of body pitch/roll. */
    public static void cable(CarEntity car,PoseStack poses,MultiBufferSource buffers,int light){
        if(!car.plugged())return;
        Vec3 start=new Vec3(1,.8,-1.44).yRot((float)-Math.toRadians(car.getYRot()));
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
