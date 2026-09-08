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
    public static InstrumentReadings read(CarEntity c){
        var r=InstrumentReadings.read(c.mechanics(),c.horizontalSpeed(),c.rpm(),c.fuel(),c.temperature(),c.oilPressure(),c.boost(),c.engineRunning());
        if(!c.powertrain().electric())return r;
        var warnings=new java.util.ArrayList<String>();
        if(c.stateOfCharge()<.1)warnings.add("LOW SOC");if(c.packTemperature()>55)warnings.add("PACK HOT");
        if(!com.photonspark.sparkmotors.sim.PowertrainTopology.liveHv(c.mechanics()))warnings.add("HV OPEN");
        if(c.motorTemperature()>140||c.inverterTemperature()>100)warnings.add("DRIVE HOT");
        if(c.coolant()<2||c.temperature()>110)warnings.add("COOLANT");
        if(c.powertrain().hybrid()&&c.generatorRunning()&&c.oilPressure()<.65)warnings.add("OIL");
        if(c.brakeFluid()<.2)warnings.add("BRAKE");if(c.powertrain().hybrid()&&c.fuel()<5)warnings.add("FUEL");if(c.plugged())warnings.add("PLUGGED");
        return new InstrumentReadings(r.powered(),r.speed(),c.motorRpm(),r.fuel(),c.packTemperature(),Double.NaN,c.accessoryVoltage(),0,r.odometer(),java.util.List.copyOf(warnings));
    }
    public static double fraction(CarEntity c,InstrumentReadings r,String gauge){
        if(!c.powertrain().electric())return r.fraction(gauge);if(!r.powered())return 0;
        return switch(gauge){case "rpm"->Math.clamp(c.motorRpm()/18000,0,1);case "fuel"->c.stateOfCharge();case "coolant"->Math.clamp(c.packTemperature()/80,0,1);case "oil"->0;default->r.fraction(gauge);};
    }
    public static void render(CarEntity c,PoseStack poses,MultiBufferSource buffers,int light){
        var r=read(c);if(!r.powered())return;var font=Minecraft.getInstance().font;
        poses.pushPose();poses.translate(-.19,1.011,.365);poses.mulPose(Axis.YP.rotationDegrees(180));poses.scale(.00125f,-.00125f,.00125f);
        String gear=c.gear()<0?"R":Integer.toString(c.gear());
        font.drawInBatch(String.format(Locale.ROOT,"G %s  ODO %.2f km",gear,r.odometer()),0,0,0xFF71D6AB,false,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        font.drawInBatch(String.format(Locale.ROOT,"TRIP %.2f  %s",c.tripKm(),String.join(" ",r.warnings())),0,11,r.warnings().isEmpty()?0xFF71D6AB:0xFFFFA04D,false,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        poses.popPose();
        String[] labels=c.powertrain().electric()?new String[]{"SOC","PACK","--","12V"}:new String[]{"FUEL","C","OIL","V"};double[] centers={-.22,-.13,-.04,.05};
        for(int i=0;i<4;i++){poses.pushPose();poses.translate(centers[i]+.022,1.085,.364);poses.mulPose(Axis.YP.rotationDegrees(180));poses.scale(.0015f,-.0015f,.0015f);font.drawInBatch(labels[i],0,0,0xFFD7E6E2,false,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);poses.popPose();}
    }
}
