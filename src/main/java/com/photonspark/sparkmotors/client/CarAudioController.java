package com.photonspark.sparkmotors.client;

import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.*;
import com.photonspark.sparkmotors.sound.VehicleSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import java.util.*;

/** At most six nearest cars; clears on world changes and recovers after F3+T/audio reload. */
public final class CarAudioController {
    private static final Map<Integer,Playing> CARS=new HashMap<>();
    private static Object world;
    public static int activeSourceCount(int carId){var state=CARS.get(carId);return state==null?0:(int)state.layers.values().stream().filter(Minecraft.getInstance().getSoundManager()::isActive).count();}
    private static final class Playing {
        final CarEntity car; final Map<String,CarEngineSound> layers=new HashMap<>();
        float previousThrottle,previousBoost; int previousGear,lastBov=-100; boolean wasRunning;
        Playing(CarEntity car){this.car=car;previousGear=car.gear();previousThrottle=car.throttle();previousBoost=car.boost();wasRunning=car.ignition();}
        void close(){layers.values().forEach(CarEngineSound::finish);layers.clear();}
        void tick(){
            var mc=Minecraft.getInstance();
            var voices=VehicleAudio.mix(car.engineFamily(),car.config(),car.engineParts(),car.condition(),car.ignition(),car.grounded(),car.rpm(),car.throttle(),car.speed(),car.boost(),car.slip(),car.braking());
            layers.entrySet().removeIf(entry->{var next=voices.get(entry.getKey());
                if(next==null||!next.event().equals(entry.getValue().event)||entry.getValue().isStopped()){
                    entry.getValue().finish();return true;
                }return false;
            });
            for(var entry:voices.entrySet()){
                var sound=layers.get(entry.getKey());
                if(sound==null){sound=new CarEngineSound(car,entry.getValue());layers.put(entry.getKey(),sound);sound.nextRetryTick=car.tickCount+40;mc.getSoundManager().play(sound);}
                else {sound.set(entry.getValue());if(car.tickCount>=sound.nextRetryTick&&!mc.getSoundManager().isActive(sound)){sound.nextRetryTick=car.tickCount+40;mc.getSoundManager().play(sound);}}
            }
            if(wasRunning&&car.ignition()&&EnginePart.INDUCTION.variant(car.engineParts())==1&&previousThrottle>.5&&car.throttle()<.5&&previousBoost>.15&&car.tickCount-lastBov>8){
                oneShot("bov",.38f);lastBov=car.tickCount;
            }
            if(wasRunning&&car.ignition()&&car.gear()!=previousGear&&Math.abs(car.speed())>2)oneShot("shift",.2f);
            previousGear=car.gear();previousThrottle=car.throttle();previousBoost=car.boost();wasRunning=car.ignition();
        }
        void oneShot(String event,float volume){car.level().playLocalSound(car.getX(),car.getY()+.6,car.getZ(),VehicleSounds.get(event),SoundSource.NEUTRAL,volume,1,false);}
    }
    public static void tick(){
        var mc=Minecraft.getInstance();
        if(world!=mc.level||mc.player==null){CARS.values().forEach(Playing::close);CARS.clear();world=mc.level;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        var nearby=mc.level.getEntitiesOfClass(CarEntity.class,mc.player.getBoundingBox().inflate(48)).stream()
            .sorted(Comparator.comparingDouble(car->car.distanceToSqr(mc.player))).limit(6).toList();
        var keep=new HashSet<Integer>();
        for(var car:nearby){keep.add(car.getId());var p=CARS.get(car.getId());
            if(p==null||p.car!=car){if(p!=null)p.close();p=new Playing(car);CARS.put(car.getId(),p);}p.tick();}
        CARS.entrySet().removeIf(e->{if(!keep.contains(e.getKey())){e.getValue().close();return true;}return false;});
    }
}
