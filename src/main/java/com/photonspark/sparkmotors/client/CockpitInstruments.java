package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.InstrumentReadings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import java.util.Locale;

public final class CockpitInstruments {
    public static InstrumentReadings read(CarEntity c){return InstrumentReadings.read(c.mechanics(),c.horizontalSpeed(),c.rpm(),c.fuel(),c.temperature(),c.oilPressure(),c.boost(),c.engineRunning());}
    public static void render(CarEntity c,PoseStack poses,MultiBufferSource buffers,int light){
        var r=read(c);if(!r.powered())return;var font=Minecraft.getInstance().font;
        poses.pushPose();poses.translate(-.19,1.011,.365);poses.mulPose(Axis.YP.rotationDegrees(180));poses.scale(.00125f,-.00125f,.00125f);
        String gear=c.gear()<0?"R":Integer.toString(c.gear());
        font.drawInBatch(String.format(Locale.ROOT,"G %s  ODO %.2f km",gear,r.odometer()),0,0,0xFF71D6AB,false,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        font.drawInBatch(String.format(Locale.ROOT,"TRIP %.2f  %s",c.tripKm(),String.join(" ",r.warnings())),0,11,r.warnings().isEmpty()?0xFF71D6AB:0xFFFFA04D,false,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        poses.popPose();
        String[] labels={"FUEL","C","OIL","V"};double[] centers={-.22,-.13,-.04,.05};
        for(int i=0;i<4;i++){poses.pushPose();poses.translate(centers[i]+.022,1.085,.364);poses.mulPose(Axis.YP.rotationDegrees(180));poses.scale(.0015f,-.0015f,.0015f);font.drawInBatch(labels[i],0,0,0xFFD7E6E2,false,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);poses.popPose();}
    }
}
