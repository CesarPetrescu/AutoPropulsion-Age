package com.photonspark.autopropulsion;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

@Mod(AutoPropulsion.ID)
public final class AutoPropulsion {
    public static final String ID = "autopropulsion";
    public static final Logger LOG = LogUtils.getLogger();
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredHolder<EntityType<?>, EntityType<VehicleEntity>> HATCH = ENTITIES.register("hatch",
        () -> EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC).sized(1.9f, 1.52f)
            .clientTrackingRange(12).updateInterval(1).build(ID + ":hatch"));
    public static final DeferredBlock<WorkshopBlock> GARAGE = BLOCKS.register("garage_lift", () -> new WorkshopBlock(BlockBehaviour.Properties.of().strength(3f), false));
    public static final DeferredBlock<WorkshopBlock> DYNO = BLOCKS.register("dyno", () -> new WorkshopBlock(BlockBehaviour.Properties.of().strength(3f), true));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkshopBlockEntity>> WORKSHOP_ENTITY = BLOCK_ENTITIES.register("workshop",
        () -> BlockEntityType.Builder.of(WorkshopBlockEntity::new, GARAGE.get(), DYNO.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<GarageMenu>> GARAGE_MENU = MENUS.register("garage", () -> IMenuTypeExtension.create(GarageMenu::new));
    public static final DeferredItem<PartItem> PART = ITEMS.registerItem("part", PartItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChassisKitItem> CHASSIS = ITEMS.registerItem("chassis_kit", ChassisKitItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> WRENCH = ITEMS.registerSimpleItem("wrench", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> LAPTOP = ITEMS.registerSimpleItem("laptop", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> FUEL_CAN = ITEMS.registerSimpleItem("fuel_can", new Item.Properties().stacksTo(1));
    static {
        ITEMS.registerSimpleBlockItem("garage_lift", GARAGE);
        ITEMS.registerSimpleBlockItem("dyno", DYNO);
        TABS.register("workshop", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.autopropulsion"))
            .icon(() -> new ItemStack(CHASSIS.get())).displayItems((parameters, output) -> {
                output.accept(CHASSIS.get()); output.accept(WRENCH.get()); output.accept(LAPTOP.get());
                output.accept(FUEL_CAN.get()); output.accept(GARAGE.get()); output.accept(DYNO.get());
                PartCatalog.builtinIds().forEach(id -> output.accept(PartItem.stack(id)));
            }).build());
    }
    public AutoPropulsion(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus); BLOCKS.register(modBus); ENTITIES.register(modBus); MENUS.register(modBus);
        BLOCK_ENTITIES.register(modBus); TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ApaConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ApaConfig.CLIENT_SPEC);
        modBus.addListener(InputPayload::register);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new PartCatalog()));
        NeoForge.EVENT_BUS.addListener(DevelopmentHooks::commands);
        NeoForge.EVENT_BUS.addListener(DevelopmentHooks::login);
        LOG.info("AutoPropulsion Age: Minecraft 1.21.1 / NeoForge, development alpha");
    }
}
