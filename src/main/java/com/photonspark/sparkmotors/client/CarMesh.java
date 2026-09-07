package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.Assembly;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

public final class CarMesh {
    private static final net.minecraft.resources.ResourceLocation WHITE=AutoPropulsionAge.id("textures/entity/white.png");
    private static List<Chunk> chunks=List.of();
    private record Chunk(String name,int category,int group,int variant,int hinge,float px,float py,float pz,float angle,int kind,float[] vertices,int[] colors){}
    public static void reload(ResourceManager resources){
        var result=new ArrayList<Chunk>();
        try(var in=new DataInputStream(new GZIPInputStream(resources.open(AutoPropulsionAge.id("models/entity/sedan.mesh.gz"))))){
            if(in.readInt()!=0x41504131)throw new IOException("Unknown car mesh format");
            int count=in.readInt();if(count<0||count>10000)throw new IOException("Invalid chunk count");
            for(int i=0;i<count;i++){
                String name=in.readUTF();int cat=in.readInt(),group=in.readInt(),variant=in.readInt(),hinge=in.readInt();
                float px=in.readFloat(),py=in.readFloat(),pz=in.readFloat(),angle=in.readFloat();int kind=in.readInt(),n=in.readInt();
                if(n<0||n>2000000||n%3!=0)throw new IOException("Invalid vertex count");
                float[] v=new float[n*6];int[] colors=new int[n];
                for(int j=0;j<n;j++){for(int k=0;k<6;k++)v[j*6+k]=in.readFloat();colors[j]=in.readInt();}
                result.add(new Chunk(name,cat,group,variant,hinge,px,py,pz,angle,kind,v,colors));
            }
        }catch(IOException e){throw new IllegalStateException("Could not load AutoPropulsion car geometry",e);}
        chunks=List.copyOf(result);
    }
    public static void render(CarEntity car,float partial,PoseStack poses,MultiBufferSource buffers,int light,boolean preview){
        float panel=Mth.lerp(partial,car.oldPanelProgress,car.panelProgress);
        float wheel=Mth.lerp(partial,car.oldWheelAngle,car.wheelAngle);
        for(Chunk c:chunks){
            int selected=c.group>=0?Assembly.values()[c.group].variant(car.config()):1;
            if(c.group>=0&&(selected==0||(c.variant>0&&selected!=c.variant)))continue;
            if(c.group==0&&!preview&&panel<.05)continue;
            poses.pushPose();
            if(c.hinge>0){
                poses.translate(c.px,c.py,c.pz);
                poses.mulPose((c.hinge<=4?Axis.YP:Axis.XP).rotationDegrees(c.angle*panel));
                poses.translate(-c.px,-c.py,-c.pz);
            }
            if(c.category==13||c.name.startsWith("brake_disc_")||c.name.startsWith("hub_")){
                String tag=c.name.substring(c.name.length()-2);
                if(tag.matches("[fr][lr]")){
                    float x=tag.charAt(1)=='l'?-.83f:.83f,z=tag.charAt(0)=='f'?1.35f:-1.30f;
                    poses.translate(x,.34,z);
                    if(tag.charAt(0)=='f')poses.mulPose(Axis.YP.rotation(-car.steer()*.49f/(1+Math.abs(car.speed())*.045f)));
                    poses.mulPose(Axis.XP.rotation(wheel));poses.translate(-x,-.34,-z);
                }
            }
            VertexConsumer buffer=buffers.getBuffer(c.kind==2?RenderType.entityTranslucent(WHITE):RenderType.entityCutoutNoCull(WHITE));
            var pose=poses.last();int brightness=c.kind==3&&car.lights()?LightTexture.FULL_BRIGHT:light;
            for(int triangle=0;triangle<c.colors.length;triangle+=3){
                for(int k=0;k<4;k++){
                    int i=triangle+Math.min(k,2),v=i*6;
                    int color=c.kind==1?0xFF000000|car.paint():c.colors[i];
                    if(c.kind==2)color=(color&0xFFFFFF)|0x30000000;
                    if(c.group==0&&selected==2&&c.name.equals("valve_cover"))color=0xFFDBAC4C;
                    if(c.group==3&&selected==2&&c.name.startsWith("brake_caliper"))color=0xFF3FA7F5;
                    buffer.addVertex(pose,c.vertices[v],c.vertices[v+1],c.vertices[v+2]).setColor(color).setUv(.5f,.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(brightness).setNormal(pose,c.vertices[v+3],c.vertices[v+4],c.vertices[v+5]);
                }
            }
            poses.popPose();
        }
    }
}
