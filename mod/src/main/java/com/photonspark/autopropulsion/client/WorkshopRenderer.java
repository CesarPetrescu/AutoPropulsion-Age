package com.photonspark.autopropulsion.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.photonspark.autopropulsion.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
public final class WorkshopRenderer implements BlockEntityRenderer<WorkshopBlockEntity> {
    public WorkshopRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(WorkshopBlockEntity block, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); pose.translate(.5, 0, .5);
        String model = block.getBlockState().is(AutoPropulsion.DYNO.get()) ? "dyno_rollers" : "garage_lift";
        MeshRenderer.render(AutoPropulsion.id("vehicle/parts/" + model), pose, buffers, light, 0, 0, 0, true);
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(WorkshopBlockEntity block) { return true; }
}
