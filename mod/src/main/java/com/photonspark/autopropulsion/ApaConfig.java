package com.photonspark.autopropulsion;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ApaConfig {
    public static final ModConfigSpec SERVER_SPEC, CLIENT_SPEC;
    public static final ModConfigSpec.IntValue SUBSTEPS;
    public static final ModConfigSpec.DoubleValue DAMAGE, FUEL;
    public static final ModConfigSpec.BooleanValue HUD, ENGINE_ALWAYS_VISIBLE;
    public static final ModConfigSpec.IntValue DETAIL_DISTANCE;
    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        SUBSTEPS = server.comment("Fixed substeps per 20 Hz server tick. Default simulation rate: 80 Hz.").defineInRange("simulationSubsteps", 4, 1, 8);
        DAMAGE = server.comment("Structural/thermal damage multiplier. Zero disables simulated wear.").defineInRange("damageMultiplier", 1d, 0d, 10d);
        FUEL = server.comment("Fuel use multiplier. Zero disables consumption, but an empty tank still cannot start.").defineInRange("fuelConsumptionMultiplier", 1d, 0d, 10d);
        SERVER_SPEC = server.build();
        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        HUD = client.define("driverHud", true);
        ENGINE_ALWAYS_VISIBLE = client.comment("Draw engine-bay parts even with the hood closed.").define("alwaysRenderEngine", false);
        DETAIL_DISTANCE = client.defineInRange("engineDetailDistance", 24, 4, 96);
        CLIENT_SPEC = client.build();
    }
    private ApaConfig() {}
}
