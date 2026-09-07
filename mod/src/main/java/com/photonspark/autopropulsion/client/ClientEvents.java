package com.photonspark.autopropulsion.client;

import com.photonspark.autopropulsion.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Locale;

@EventBusSubscriber(modid = AutoPropulsion.ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static int carId = -1, selectedGear;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (Boolean.getBoolean("apa.clientSmoke")) { ClientSmoke.tick(mc); return; }
        if (mc.player == null || !(mc.player.getVehicle() instanceof VehicleEntity car)) { carId = -1; return; }
        if (carId != car.getId()) { carId = car.getId(); selectedGear = car.gear(); }
        if (mc.screen != null) { PacketDistributor.sendToServer(new InputPayload(car.getId(), 0, 1, 0, 1, selectedGear, 1)); return; }
        while (ClientSetup.UP.consumeClick()) selectedGear = Math.min(5, selectedGear + 1);
        while (ClientSetup.DOWN.consumeClick()) selectedGear = Math.max(-1, selectedGear - 1);
        int flags = (mc.options.keyJump.isDown() ? 1 : 0) | (ClientSetup.START.consumeClick() ? 2 : 0) | (ClientSetup.HOOD.consumeClick() ? 4 : 0);
        PacketDistributor.sendToServer(new InputPayload(car.getId(), mc.options.keyUp.isDown() ? 1 : 0, mc.options.keyDown.isDown() ? 1 : 0,
            (mc.options.keyLeft.isDown() ? -1 : 0) + (mc.options.keyRight.isDown() ? 1 : 0), ClientSetup.CLUTCH.isDown() ? 1 : 0, selectedGear, flags));
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof VehicleEntity car) || !ApaConfig.HUD.get()) return;
        var graphics = event.getGuiGraphics(); int x = graphics.guiWidth() - 206, y = graphics.guiHeight() - 104;
        graphics.fill(x, y, x + 194, y + 88, 0xDD101923);
        String gear = car.gear() < 0 ? "R" : car.gear() == 0 ? "N" : Integer.toString(car.gear());
        graphics.drawString(mc.font, String.format(Locale.ROOT, "%3.0f km/h   %s   %4.0f rpm", Math.abs(car.speed()) * 3.6, gear, car.rpm()), x + 10, y + 10, 0xFFE6EDF3);
        graphics.fill(x + 10, y + 27, x + 184, y + 33, 0xFF263442);
        graphics.fill(x + 10, y + 27, x + 10 + (int)(174 * Math.min(1, car.rpm() / 8000)), y + 33, car.rpm() > 6500 ? 0xFFED9A4E : 0xFF4DD2BD);
        graphics.drawString(mc.font, String.format(Locale.ROOT, "Fuel %.1f L   Boost %.2f bar", car.fuel(), car.boost()), x + 10, y + 41, 0xFFBDD0DE);
        graphics.drawString(mc.font, String.format(Locale.ROOT, "Coolant %.0f C   Health %.0f%%", car.coolantK() - 273.15, car.health()), x + 10, y + 55, 0xFFBDD0DE);
        graphics.drawString(mc.font, car.dtc().isEmpty() ? "E start | R/F shift | Ctrl clutch" : car.dtc(), x + 10, y + 71, car.dtc().isEmpty() ? 0xFF8395A7 : 0xFFFF9E74);
    }
}
