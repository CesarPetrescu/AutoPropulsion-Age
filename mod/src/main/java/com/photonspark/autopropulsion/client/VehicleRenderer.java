package com.photonspark.autopropulsion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.photonspark.autopropulsion.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class VehicleRenderer extends EntityRenderer<VehicleEntity> {
    public VehicleRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 1.6f; }
    @Override public ResourceLocation getTextureLocation(VehicleEntity car) { return AutoPropulsion.id("textures/white.png"); }
    @Override public void render(VehicleEntity car, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        boolean nearby = Minecraft.getInstance().player == null || Minecraft.getInstance().player.distanceToSqr(car) < Math.pow(ApaConfig.DETAIL_DISTANCE.get(), 2);
        MeshRenderer.render(AutoPropulsion.id("vehicle/hatch_01"), pose, buffers, light, car.steering(), car.wheelAngle(),
            Mth.lerp(partialTick, car.oldHoodAngle, car.hoodAngle), nearby && (car.hoodOpen() || ApaConfig.ENGINE_ALWAYS_VISIBLE.get()));
        pose.popPose(); super.render(car, yaw, partialTick, pose, buffers, light);
    }
}
