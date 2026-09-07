package com.photonspark.autopropulsion;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(AutoPropulsion.ID)
public final class AutoPropulsion {
    public static final String ID="autopropulsion";
    public static final Logger LOG=LogUtils.getLogger();
    public AutoPropulsion(IEventBus bus,ModContainer container) {
        Content.register(bus);
        bus.addListener(Packets::register);
        container.registerConfig(ModConfig.Type.SERVER,Config.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT,Config.CLIENT_SPEC);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event)->event.addListener(new PartCatalog()));
        if(Boolean.getBoolean("autopropulsion.smokeServer")) SmokeServer.register();
        LOG.info("AutoPropulsion Age: NeoForge 1.21.1 vehicle foundation loaded");
    }
}
