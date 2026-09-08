package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Lifecycle owner for all concurrent positional voices on one tracked car. */
public final class CarAudio {
    private final CarEntity car;
    private final Map<String,Layer> layers=new HashMap<>();
    private int vents;
    public CarAudio(CarEntity car){this.car=car;vents=car.ventEvents();}
    public int voices(){return (int)layers.values().stream().filter(s->Minecraft.getInstance().getSoundManager().isActive(s)).count();}
    public void stop(){layers.values().forEach(Layer::finish);layers.clear();}
    public void tick(){
        var corners=new ArrayList<WheelDynamics.Corner>();
        for(int c=0;c<4;c++)corners.add(new WheelDynamics.Corner(car.wheelOmega(c),0,car.wheelTravel(c),car.wheelContact(c),car.wheelSlip(c),0,20));
        var input=new VehicleAudio.Input(car.engineFamily(),car.engineMode(),car.rpm(),car.throttle(),car.engineLoad(),car.spool(),car.boost(),car.horizontalSpeed(),EnginePart.INDUCTION.variant(car.engineParts()),car.serviceBrake(),car.handbrake(),car.roughSurface(),new WheelDynamics.State(corners),car.mechanics());
        var mix=VehicleAudio.mix(input);
        layers.values().forEach(layer->layer.target=0);
        for(var entry:mix.entrySet()){
            var voice=entry.getValue();var layer=layers.get(entry.getKey());
            if(layer!=null&&(!layer.soundName.equals(voice.sound())||layer.isStopped())){layer.finish();layers.remove(entry.getKey());layer=null;}
            if(layer==null){layer=new Layer(car,voice.sound(),voice.corner());layers.put(entry.getKey(),layer);Minecraft.getInstance().getSoundManager().play(layer);}
            layer.target=(float)voice.gain();layer.targetPitch=(float)voice.pitch();
        }
        layers.entrySet().removeIf(e->e.getValue().isStopped());
        if(vents!=car.ventEvents()){
            vents=car.ventEvents();if(vents>0&&car.boost()>.015&&car.mechanics().capability("induction.bov")>.2)
                car.level().playLocalSound(car.getX(),car.getY()+.7,car.getZ(),AutoPropulsionAge.MECHANICAL_SOUNDS.get("release").get(),SoundSource.NEUTRAL,.28f,1,false);
        }
    }
    private static final class Layer extends AbstractTickableSoundInstance {
        final CarEntity car;final String soundName;final int corner;float target,targetPitch=1;int silent;
        Layer(CarEntity car,String name,int corner){super(AutoPropulsionAge.MECHANICAL_SOUNDS.get(name).get(),SoundSource.NEUTRAL,RandomSource.create());this.car=car;soundName=name;this.corner=corner;looping=true;delay=0;volume=.001f;pitch=1;position();}
        @Override public boolean canStartSilent(){return true;}
        void finish(){stop();}
        private void position(){Vec3 offset=corner<0?new Vec3(0,.65,soundName.startsWith("exhaust")?-1.9:1.2):new Vec3(corner%2==0?-.83:.83,.34,corner<2?1.35:-1.3);var p=car.position().add(offset.yRot((float)-Math.toRadians(car.getYRot())));x=p.x;y=p.y;z=p.z;}
        @Override public void tick(){
            var mc=Minecraft.getInstance();if(car.isRemoved()||mc.level!=car.level()||mc.player==null||car.distanceToSqr(mc.player)>2500){finish();return;}
            position();volume+=(target-volume)*.28f;pitch+=(targetPitch-pitch)*.25f;
            if(target<.002&&volume<.003){if(++silent>3)finish();}else silent=0;
        }
    }
}
