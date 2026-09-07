package com.photonspark.autopropulsion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class Content {
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,AutoPropulsion.ID);
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> ENGINE_SOUND=SOUNDS.register("engine_loop",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutoPropulsion.ID,"engine_loop")));
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(AutoPropulsion.ID);
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(AutoPropulsion.ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,AutoPropulsion.ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,AutoPropulsion.ID);
    public static final DeferredItem<Item> PART=ITEMS.register("part",()->new ComponentItem(new Item.Properties()));
    public static final DeferredItem<Item> BLUEPRINT=ITEMS.register("vehicle_blueprint",()->new VehicleBlueprint(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WRENCH=ITEMS.registerSimpleItem("mechanic_wrench",new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> JERRY_CAN=ITEMS.registerSimpleItem("jerry_can",new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> EMPTY_CAN=ITEMS.registerSimpleItem("empty_jerry_can",new Item.Properties().stacksTo(1));
    public static final DeferredBlock<Block> LIFT=BLOCKS.register("garage_lift",()->new WorkshopBlock(BlockBehaviour.Properties.of().strength(4).noOcclusion()));
    public static final DeferredBlock<Block> DYNO=BLOCKS.register("dyno",()->new WorkshopBlock(BlockBehaviour.Properties.of().strength(4).noOcclusion()));
    public static final DeferredBlock<Block> BENCH=BLOCKS.registerSimpleBlock("parts_bench",BlockBehaviour.Properties.of().strength(3));
    public static final DeferredBlock<Block> ASPHALT=BLOCKS.registerSimpleBlock("asphalt",BlockBehaviour.Properties.of().strength(2));
    public static final DeferredHolder<EntityType<?>,EntityType<VehicleEntity>> VEHICLE=ENTITIES.register("hatchback",()->EntityType.Builder.<VehicleEntity>of(VehicleEntity::new,MobCategory.MISC).sized(1.8f,1.4f).clientTrackingRange(12).updateInterval(1).build("autopropulsion:hatchback"));
    static {
        ITEMS.registerSimpleBlockItem(LIFT);ITEMS.registerSimpleBlockItem(DYNO);ITEMS.registerSimpleBlockItem(BENCH);ITEMS.registerSimpleBlockItem(ASPHALT);
        TABS.register("garage",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.autopropulsion"))
            .icon(()->BLUEPRINT.toStack()).displayItems((params,out)->{
                out.accept(BLUEPRINT);out.accept(WRENCH);out.accept(JERRY_CAN);out.accept(EMPTY_CAN);
                out.accept(LIFT);out.accept(DYNO);out.accept(BENCH);out.accept(ASPHALT);
                for(String id:BuiltinCatalog.IDS) out.accept(ComponentItem.stack(id));
            }).build());
    }
    public static void register(IEventBus bus) {SOUNDS.register(bus);ITEMS.register(bus);BLOCKS.register(bus);ENTITIES.register(bus);TABS.register(bus);}
    private Content() {}
}
