package com.photonspark.sparkmotors;

import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.item.CarCrateItem;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.Assembly;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;
import java.util.*;
import java.util.function.IntConsumer;

@Mod(AutoPropulsionAge.ID)
public final class AutoPropulsionAge {
    public static final String ID = "sparkmotors";
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> ENGINE_SOUND = SOUNDS.register("engine_loop",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id("engine_loop")));
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredHolder<EntityType<?>, EntityType<CarEntity>> CAR = ENTITIES.register("sedan",
        () -> EntityType.Builder.<CarEntity>of(CarEntity::new, MobCategory.MISC).sized(1.95f,1.52f).clientTrackingRange(12).updateInterval(1).build(ID+":sedan"));
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredItem<Item> CAR_CRATE = ITEMS.register("sedan_crate", () -> new CarCrateItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WRENCH = ITEMS.registerSimpleItem("garage_wrench", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> FUEL_CAN = ITEMS.registerSimpleItem("fuel_can",new Item.Properties().stacksTo(16));
    public static final DeferredBlock<Block> GARAGE = BLOCKS.register("garage_controller", () -> new com.photonspark.sparkmotors.item.GarageBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f)));
    public static final DeferredItem<BlockItem> GARAGE_ITEM = ITEMS.registerSimpleBlockItem(GARAGE);
    public static final Map<String,DeferredItem<Item>> PART_ITEMS = new LinkedHashMap<>();
    static {
        for (Assembly slot : Assembly.values()) for (int v=1; v<=2; v++) {
            String key=slot.itemName(v); PART_ITEMS.put(key, ITEMS.registerSimpleItem(key,new Item.Properties().stacksTo(16)));
        }
    }
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB = TABS.register("garage", () -> CreativeModeTab.builder()
        .title(Component.literal("AutoPropulsion Age")).icon(() -> CAR_CRATE.toStack())
        .displayItems((parameters,output) -> { output.accept(CAR_CRATE);output.accept(WRENCH);output.accept(FUEL_CAN);output.accept(GARAGE_ITEM);PART_ITEMS.values().forEach(output::accept); }).build());
    // Installed only by the client entry point. Dedicated servers never load UI classes.
    public static IntConsumer openGarage = id -> {};
    public AutoPropulsionAge(IEventBus bus) {
        ENTITIES.register(bus);ITEMS.register(bus);BLOCKS.register(bus);TABS.register(bus);SOUNDS.register(bus);
        bus.addListener(CarPackets::register);
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID,path); }
    public static Item partItem(Assembly slot,int variant) { return PART_ITEMS.get(slot.itemName(variant)).get(); }
}
