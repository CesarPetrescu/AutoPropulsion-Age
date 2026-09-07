package com.photonspark.autopropulsion.client;
import com.photonspark.autopropulsion.*;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One synthetic placeholder loop per nearby running vehicle, bounded by ClientEvents. */
public final class EngineLoop extends AbstractTickableSoundInstance {
    private final VehicleEntity car;
    public EngineLoop(VehicleEntity car){super(Content.ENGINE_SOUND.get(),SoundSource.NEUTRAL,RandomSource.create());this.car=car;looping=true;delay=0;volume=.12f;tick();}
    @Override public void tick(){if(car.isRemoved()||!car.running()){stop();return;}x=car.getX();y=car.getY();z=car.getZ();pitch=Math.max(.5f,Math.min(2,car.rpm()/2400));volume=.1f+Math.min(.22f,car.rpm()/30000);}
}
