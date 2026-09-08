package com.photonspark.sparkmotors.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.nbt.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Predicate;

/** Typed, immutable, versioned item data; Minecraft's codec also supplies bounded network serialization. */
public final class MechanicalData {
    public static final Codec<PartInstance> PART=RecordCodecBuilder.create(i->i.group(
        net.minecraft.core.UUIDUtil.CODEC.fieldOf("id").forGetter(PartInstance::id),
        Codec.STRING.fieldOf("item").forGetter(PartInstance::item),
        Codec.DOUBLE.optionalFieldOf("wear",0d).forGetter(PartInstance::wear),
        Codec.DOUBLE.optionalFieldOf("damage",0d).forGetter(PartInstance::damage),
        Codec.INT.optionalFieldOf("faults",0).forGetter(PartInstance::faults),
        Codec.DOUBLE.optionalFieldOf("reserve",0d).forGetter(PartInstance::reserve),
        Codec.DOUBLE.optionalFieldOf("temperature",20d).forGetter(PartInstance::temperature)
    ).apply(i,PartInstance::new));
    public static final Codec<MechanicalState> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.intRange(1,MechanicalState.VERSION).fieldOf("version").forGetter(MechanicalState::version),
        Codec.unboundedMap(Codec.STRING,PART).fieldOf("parts").forGetter(MechanicalState::parts),
        Codec.DOUBLE.optionalFieldOf("coolant",0d).forGetter(MechanicalState::coolant),
        Codec.DOUBLE.optionalFieldOf("oil",0d).forGetter(MechanicalState::oil),
        Codec.DOUBLE.optionalFieldOf("brake_fluid",0d).forGetter(MechanicalState::brakeFluid),
        Codec.DOUBLE.optionalFieldOf("coolant_temperature",20d).forGetter(MechanicalState::coolantTemperature),
        Codec.DOUBLE.optionalFieldOf("oil_temperature",20d).forGetter(MechanicalState::oilTemperature),
        Codec.DOUBLE.optionalFieldOf("distance",0d).forGetter(MechanicalState::distance),
        Codec.STRING.listOf().xmap(Set::copyOf,ArrayList::new).optionalFieldOf("fault_history",Set.of()).forGetter(MechanicalState::faultHistory)
    ).apply(i,MechanicalState::new));
    public static DataComponentType<MechanicalState> type(){return DataComponentType.<MechanicalState>builder().persistent(CODEC).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(CODEC)).build();}
    public static CompoundTag write(MechanicalState state){return (CompoundTag)CODEC.encodeStart(NbtOps.INSTANCE,state).getOrThrow();}
    public static MechanicalState read(CompoundTag tag){return CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();}
    public static MechanicalState get(ItemStack stack){return stack.get(com.photonspark.sparkmotors.AutoPropulsionAge.MECHANICAL_DATA.get());}
    public static ItemStack set(ItemStack stack,MechanicalState state){stack.set(com.photonspark.sparkmotors.AutoPropulsionAge.MECHANICAL_DATA.get(),state);return stack;}
    public static MechanicalState bundle(ItemStack stack,Predicate<ComponentSlot> selection,int config,int hardware){
        var stored=get(stack);
        if(stored!=null)return stored;
        // Only unversioned legacy / freshly crafted items receive initial identities. Empty stored slots stay empty.
        return MechanicalState.legacy(config,hardware,EngineItem.temperature(stack),EngineItem.oilTemperature(stack),EngineItem.health(stack)).select(selection,selection.test(ComponentSlot.engine(EnginePart.INTERNALS)));
    }
    public static ItemStack single(ItemStack stack,ComponentSlot slot,PartInstance part){return set(stack,MechanicalState.empty().with(slot.key(),part));}
    public static PartInstance part(ItemStack stack,ComponentSlot slot){
        var stored=get(stack);
        if(stored==null)return PartInstance.fresh(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),slot.key().endsWith(".tire")?2.3:slot.key().equals("electrical.battery")?48:0);
        // A used tire may move to a different corner without changing identity or pressure.
        if(slot.hardware()==EnginePart.INTERNALS){var root=stored.get(slot.key());return root!=null&&slot.accepts(root)?root:null;}
        if(stored.parts().size()!=1)return null;
        var part=stored.parts().values().iterator().next();return slot.accepts(part)?part:null;
    }
    public static MechanicalState internalBundle(ItemStack stack,EngineFamily family,ComponentSlot slot,PartInstance root){
        var stored=get(stack);
        if(stored==null)stored=new MechanicalState(1,Map.of(slot.key(),root),0,0,0,20,20,0,Set.of());
        // Original v1 single internal kits represented all internals. Only those acquire child identities.
        stored=InternalMechanics.migrateBundle(stored,family);
        if(!stored.validFor(s->s.key().equals(slot.key())||InternalMechanics.internal(s.key())))return null;
        return stored;
    }
}
