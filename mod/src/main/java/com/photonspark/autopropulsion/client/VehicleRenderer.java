package com.photonspark.autopropulsion.client;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.photonspark.autopropulsion.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Renders actual Blender-exported triangles, grouped at wheel and body locators. */
public final class VehicleRenderer extends EntityRenderer<VehicleEntity> {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath(AutoPropulsion.ID,"textures/entity/white.png");
    private static final ResourceLocation MESH=ResourceLocation.fromNamespaceAndPath(AutoPropulsion.ID,"models/vehicle/hatchback.mesh.json");
    private record Face(float[][] vertices,float[] normal,int color){}
    private record Group(String name,float[] pivot,List<Face> faces){}
    private static List<Group> cached;
    public VehicleRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=1.3f;}
    public static void clearCache(){cached=null;}
    private static float[] vector(JsonArray a){return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};}
    private static List<Group> mesh() {
        if(cached!=null)return cached;
        var result=new ArrayList<Group>();
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(MESH)) {
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            for(var ge:root.getAsJsonArray("groups")) {
                var group=ge.getAsJsonObject();var faces=new ArrayList<Face>();
                for(var fe:group.getAsJsonArray("faces")) {
                    var f=fe.getAsJsonObject();var vs=f.getAsJsonArray("v");float[][] v={vector(vs.get(0).getAsJsonArray()),vector(vs.get(1).getAsJsonArray()),vector(vs.get(2).getAsJsonArray())};
                    faces.add(new Face(v,vector(f.getAsJsonArray("n")),Integer.parseInt(f.get("c").getAsString(),16)));
                }
                result.add(new Group(group.get("name").getAsString(),vector(group.getAsJsonArray("pivot")),List.copyOf(faces)));
            }
        } catch(Exception e){throw new IllegalStateException("Cannot load AutoPropulsion vehicle mesh; regenerate assets",e);}
        if(result.isEmpty())throw new IllegalStateException("Empty vehicle mesh");
        cached=List.copyOf(result);AutoPropulsion.LOG.info("Loaded Blender vehicle mesh: {} groups",cached.size());return cached;
    }
    @Override public ResourceLocation getTextureLocation(VehicleEntity entity){return WHITE;}
    @Override public void render(VehicleEntity car,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        for(Group group:mesh()) {
            if(!Config.DETAIL.get()&&group.name.startsWith("engine"))continue;
            poses.pushPose();poses.translate(group.pivot[0],group.pivot[1],group.pivot[2]);
            if(group.name.startsWith("wheel")) {
                if(group.name.contains("front"))poses.mulPose(Axis.YP.rotationDegrees(car.steer()*-27));
                poses.mulPose(Axis.XP.rotationDegrees((car.tickCount+partial)*car.speed()*.05f/.31f*180/(float)Math.PI));
            }
            PoseStack.Pose pose=poses.last();
            for(Face face:group.faces) {
                int r=(face.color>>16)&255,g=(face.color>>8)&255,b=face.color&255;
                // Vanilla entity render types consume quads; duplicate the third triangle vertex.
                for(int i:new int[]{0,1,2,2}) {
                    float[] v=face.vertices[i];
                    out.addVertex(pose.pose(),v[0],v[1],v[2]).setColor(r,g,b,255).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,face.normal[0],face.normal[1],face.normal[2]);
                }
            }
            poses.popPose();
        }
        poses.popPose();super.render(car,yaw,partial,poses,buffers,light);
    }
}
