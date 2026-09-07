package com.photonspark.autopropulsion.client;

import com.photonspark.autopropulsion.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AutoPropulsion.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    public static final KeyMapping START = key("start", GLFW.GLFW_KEY_E);
    public static final KeyMapping UP = key("up", GLFW.GLFW_KEY_R);
    public static final KeyMapping DOWN = key("down", GLFW.GLFW_KEY_F);
    public static final KeyMapping CLUTCH = key("clutch", GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping HOOD = key("hood", GLFW.GLFW_KEY_K);
    private static KeyMapping key(String name, int code) { return new KeyMapping("key.autopropulsion." + name, code, "key.categories.autopropulsion"); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AutoPropulsion.HATCH.get(), VehicleRenderer::new);
        event.registerBlockEntityRenderer(AutoPropulsion.WORKSHOP_ENTITY.get(), WorkshopRenderer::new);
    }
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event) { event.register(AutoPropulsion.GARAGE_MENU.get(), GarageScreen::new); }
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        event.register(START); event.register(UP); event.register(DOWN); event.register(CLUTCH); event.register(HOOD);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> MeshRenderer.clear());
    }
}
