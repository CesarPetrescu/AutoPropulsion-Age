package com.photonspark.sparkmotors.client;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
public final class CarEngineSound extends AbstractTickableSoundInstance {
    private final CarEntity car;
    public CarEngineSound(CarEntity car){super(AutoPropulsionAge.ENGINE_SOUND.get(),SoundSource.NEUTRAL,RandomSource.create());this.car=car;looping=true;delay=0;volume=.4f;tick();}
    @Override public void tick(){
        var mc=Minecraft.getInstance();
        if(car.isRemoved()||!car.combustionRunning()||mc.level!=car.level()||mc.player==null||car.distanceToSqr(mc.player)>2500){stop();return;}
        x=car.getX();y=car.getY()+.6;z=car.getZ();pitch=Math.clamp(.65f+car.rpm()/2800,.7f,2.8f);volume=.30f+car.rpm()/15000;
    }
}
