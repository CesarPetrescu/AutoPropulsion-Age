package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sound.VehicleSounds;
import com.photonspark.sparkmotors.sim.VehicleAudio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One positional, smoothed layer. Controller owns lifetime and resource-reload recovery. */
public final class CarEngineSound extends AbstractTickableSoundInstance {
    final CarEntity car;
    final String event;
    int nextRetryTick;
    private float targetVolume,targetPitch;
    public CarEngineSound(CarEntity car,VehicleAudio.Voice voice){
        super(VehicleSounds.get(voice.event()),SoundSource.NEUTRAL,RandomSource.create());
        this.car=car;event=voice.event();looping=true;delay=0;volume=.0001f;pitch=voice.pitch();set(voice);
        x=car.getX();y=car.getY()+.6;z=car.getZ();
    }
    public void set(VehicleAudio.Voice voice){targetVolume=voice.volume();targetPitch=voice.pitch();}
    public void finish(){stop();}
    @Override public boolean canStartSilent(){return true;}
    @Override public void tick(){
        var mc=Minecraft.getInstance();
        if(car.isRemoved()||mc.level!=car.level()||mc.player==null||car.distanceToSqr(mc.player)>4096){stop();return;}
        x=car.getX();y=car.getY()+.6;z=car.getZ();
        pitch+=(targetPitch-pitch)*.22f;volume+=(targetVolume-volume)*.18f;
    }
}
