package com.photonspark.sparkmotors.client;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
public final class CarRenderer extends EntityRenderer<CarEntity> {
    public CarRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=1.65f;}
    @Override public void render(CarEntity car,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        ElectricGeometry.cable(car,poses,buffers,light);
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        poses.mulPose(Axis.XP.rotationDegrees(-car.roadPitch()));poses.mulPose(Axis.ZP.rotationDegrees(car.roadRoll()));
        CarMesh.render(car,partial,poses,buffers,light,false);poses.popPose();super.render(car,yaw,partial,poses,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(CarEntity e){return AutoPropulsionAge.id("textures/entity/white.png");}
}
