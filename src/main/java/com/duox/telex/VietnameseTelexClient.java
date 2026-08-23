package com.duox.telex;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entry point: wires config, keybinding registration and the
 * chat-typing event handlers.
 */
@Mod(value = VietnameseTelex.MODID, dist = Dist.CLIENT)
public class VietnameseTelexClient {

    public VietnameseTelexClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, TelexConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(ClientHooks::registerKeyMappings);

        NeoForge.EVENT_BUS.addListener(ClientHooks::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onCharTyped);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onKeyPressed);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onKeyInput);
        NeoForge.EVENT_BUS.addListener(ClientHooks::onRender);

        VietnameseTelex.LOGGER.info("[VietnameseTelex] client init - builtin telex ready");
    }
}
