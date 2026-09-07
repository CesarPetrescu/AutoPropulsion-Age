package com.photonspark.sparkmotors.sound;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.sim.VehicleAudio;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.*;

/** Registry is common-side; client sound instances are never loaded by a server. */
public final class VehicleSounds {
    private static final Map<String,DeferredHolder<SoundEvent,SoundEvent>> EVENTS=new LinkedHashMap<>();
    private VehicleSounds() {}
    public static void register(){
        if(!EVENTS.isEmpty())return;
        for(String name:VehicleAudio.events())EVENTS.put(name,AutoPropulsionAge.SOUNDS.register(name,
            ()->SoundEvent.createVariableRangeEvent(AutoPropulsionAge.id(name))));
    }
    public static SoundEvent get(String name){return Objects.requireNonNull(EVENTS.get(name),"Unknown vehicle sound: "+name).get();}
}
