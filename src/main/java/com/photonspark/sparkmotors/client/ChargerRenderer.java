package com.photonspark.sparkmotors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.photonspark.sparkmotors.charging.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import java.util.Locale;

/** Three compact live readings on the existing front screen; full details open on click. */
public final class ChargerRenderer implements BlockEntityRenderer<ChargerBlockEntity> {
    private final Font font;
    public ChargerRenderer(BlockEntityRendererProvider.Context context){font=context.getFont();}
    @Override public int getViewDistance(){return 24;}
    @Override public void render(ChargerBlockEntity c,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        int tier=c.tier().ordinal();float top=(tier==0?10:tier==1?12:13)/16f;
        pose.pushPose();pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180-c.getBlockState().getValue(ChargerBlock.FACING).toYRot()));
        pose.translate(0,top-.018,3.5/16d-.5-.002);pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(.0035f,-.0035f,.0035f);
        String[] lines={c.status().replace('_',' '),String.format(Locale.ROOT,"%.1f kW",c.inputKw()),c.soc()<0?(c.hasCable()?"RECOVER CABLE":"CLICK TO OPEN"):String.format(Locale.ROOT,"%.1f%% / %d%%",c.soc()*100,c.target())};
        for(int i=0;i<lines.length;i++)font.drawInBatch(lines[i],-font.width(lines[i])/2f,i*13,0xFF07191C,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,0xF000F0);
        pose.popPose();
    }
}
