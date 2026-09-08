package com.photonspark.sparkmotors.charging;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.item.CarCrateItem;
import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import java.util.*;

public final class Electrification {
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,ElectricCrateRecipe.Serializer> CRATE_RECIPE=AutoPropulsionAge.RECIPES.register("electric_crate_crafting",ElectricCrateRecipe.Serializer::new);
    public static final Map<Powertrain,DeferredItem<TractionBatteryItem>> PACKS=new EnumMap<>(Powertrain.class);
    public static final Map<Powertrain,DeferredItem<? extends Item>> CRATES=new EnumMap<>(Powertrain.class);
    public static final Map<ChargingModel.Tier,DeferredBlock<ChargerBlock>> CHARGERS=new EnumMap<>(ChargingModel.Tier.class);
    public static final List<DeferredItem<? extends Item>> ITEMS=new ArrayList<>();
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,AutoPropulsionAge.ID);
    public static final DeferredItem<ChargingCableItem> CABLE=AutoPropulsionAge.ITEMS.register("charging_cable",()->new ChargingCableItem(new Item.Properties().stacksTo(1)));
    static {
        ITEMS.add(CABLE);
        for(var type:Powertrain.values())if(type.electric()){
            var item=AutoPropulsionAge.ITEMS.register(type.id()+"_crate",()->new CarCrateItem(new Item.Properties().stacksTo(1),type));
            CRATES.put(type,item);ITEMS.add(item);
            var pack=AutoPropulsionAge.ITEMS.register(type.id()+"_battery_pack",()->new TractionBatteryItem(new Item.Properties().stacksTo(1),type));PACKS.put(type,pack);ITEMS.add(pack);
        }
        for(var tier:ChargingModel.Tier.values()){
            var block=AutoPropulsionAge.BLOCKS.register(tier.id(),()->new ChargerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4).requiresCorrectToolForDrops(),tier));
            CHARGERS.put(tier,block);ITEMS.add(AutoPropulsionAge.ITEMS.registerSimpleBlockItem(block));
        }
    }
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ChargerBlockEntity>> CHARGER_ENTITY=BLOCK_ENTITIES.register("vehicle_charger",()->BlockEntityType.Builder.of(ChargerBlockEntity::new,CHARGERS.values().stream().map(DeferredBlock::get).toArray(net.minecraft.world.level.block.Block[]::new)).build(null));
    public static void init(IEventBus bus){BLOCK_ENTITIES.register(bus);}
    private Electrification(){}
}
