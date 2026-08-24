package com.duox.telex;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;

/**
 * Client-only entry point: wires config, keybinding registration and the
 * chat-typing event handlers.
 */
public class VietnameseTelexClient {

    public VietnameseTelexClient(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TelexConfig.SPEC);

        modEventBus.addListener(ClientHooks::registerKeyMappings);

        MinecraftForge.EVENT_BUS.addListener(ClientHooks::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::onCharTyped);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::onKeyPressed);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::onKeyInput);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::onRender);

        VietnameseTelex.LOGGER.info("[VietnameseTelex] client init - builtin telex ready");
    }
}
