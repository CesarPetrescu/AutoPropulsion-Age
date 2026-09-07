package com.photonspark.autopropulsion.client;

import com.photonspark.autopropulsion.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opt-in visual/in-world smoke driver using the same validated packets as ordinary input. */
public final class ClientSmoke {
    private static int ticks, idleTicks;
    private static Vec3 start;
    private static boolean menuSeen, connectionAttempted;
    private static String profile = System.getProperty("apa.profile", "fancy"), previousScreen = "";
    private ClientSmoke() {}
    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "apa-" + profile + "-" + name + ".png", mc.getMainRenderTarget(),
            message -> AutoPropulsion.LOG.info("APA_SCREENSHOT {}", message.getString()));
    }
    public static void tick(Minecraft mc) {
        if (mc.player == null || !(mc.player.getVehicle() instanceof VehicleEntity car)) {
            idleTicks++;
            String screen = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
            if (!screen.equals(previousScreen)) { AutoPropulsion.LOG.info("APA_SMOKE_STARTUP_SCREEN={}", screen); previousScreen = screen; }
            // A clean development launch may stop at first-run accessibility onboarding instead of processing quick play.
            // This bypass applies only to the explicitly enabled disposable smoke client.
            if (!connectionAttempted && mc.getOverlay() == null && screen.equals("AccessibilityOnboardingScreen")) mc.setScreen(new TitleScreen());
            if (!connectionAttempted && mc.getOverlay() == null && mc.screen instanceof TitleScreen && idleTicks > 20) {
                connectionAttempted = true;
                String host = System.getProperty("apa.smokeHost", "127.0.0.1:25565");
                AutoPropulsion.LOG.info("APA_SMOKE_CONNECTING={}", host);
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(host),
                    new ServerData("AutoPropulsion disposable test", host, ServerData.Type.OTHER), false, null);
            }
            if (idleTicks > 2400) {
                shot(mc, "failure-startup");
                AutoPropulsion.LOG.error("APA_CLIENT_SMOKE_FAILED: no mounted vehicle; screen={}", screen); mc.stop();
            }
            return;
        }
        if (ticks++ == 0) {
            start = car.position();
            mc.options.graphicsMode().set(profile.equals("fast") ? GraphicsStatus.FAST : profile.equals("fabulous") ? GraphicsStatus.FABULOUS : GraphicsStatus.FANCY);
            mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.options.renderDistance().set(5); mc.options.simulationDistance().set(5);
            mc.player.setXRot(16); mc.player.setYRot(25);
            AutoPropulsion.LOG.info("APA_CLIENT_SMOKE_CONNECTED profile={} graphics={} vehicle={}", profile, mc.options.graphicsMode().get(), car.getId());
        }
        int gear = Math.abs(car.speed()) < 13 ? 1 : Math.abs(car.speed()) < 23 ? 2 : 3;
        float throttle = ticks >= 60 && ticks < 160 ? 1 : 0, brake = ticks >= 160 ? 1 : 0;
        int flags = (ticks == 60 ? 2 : 0) | (ticks == 300 ? 4 : 0);
        if (ticks < 365) PacketDistributor.sendToServer(new InputPayload(car.getId(), throttle, brake, 0, 0, gear, flags));
        if (ticks == 45) shot(mc, "parked");
        if (ticks == 140) shot(mc, "driving-hud");
        if (ticks == 290) {
            double distance = car.position().distanceTo(start);
            AutoPropulsion.LOG.info("APA_CLIENT_DRIVE_DISTANCE_METRES={}", distance);
            if (distance < 5 || !Float.isFinite(car.rpm())) {
                shot(mc, "failure-driving"); AutoPropulsion.LOG.error("APA_CLIENT_SMOKE_FAILED: driving did not advance the server-owned vehicle"); mc.stop(); return;
            }
        }
        if (ticks == 325) { mc.player.setYRot(150); mc.player.setXRot(22); }
        if (ticks == 340) shot(mc, "hood");
        if (ticks == 370 && mc.gameMode != null) {
            mc.player.getInventory().selected = 0;
            mc.gameMode.interact(mc.player, car, InteractionHand.MAIN_HAND);
        }
        if (ticks == 410) {
            menuSeen = mc.screen instanceof GarageScreen;
            if (!menuSeen) { shot(mc, "failure-workshop"); AutoPropulsion.LOG.error("APA_CLIENT_SMOKE_FAILED: workshop menu did not open"); mc.stop(); return; }
            shot(mc, "diagnostics");
        }
        if (ticks == 430 && mc.screen instanceof GarageScreen screen) screen.setTab(1);
        if (ticks == 455) shot(mc, "dyno");
        if (ticks == 470 && mc.screen instanceof GarageScreen screen) screen.setTab(2);
        if (ticks == 495) shot(mc, "parts");
        if (ticks == 520) {
            AutoPropulsion.LOG.info("APA_CLIENT_SMOKE_COMPLETE profile={} graphics={} menu={}", profile, mc.options.graphicsMode().get(), menuSeen);
            mc.stop();
        }
    }
}
