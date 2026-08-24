package com.duox.telex;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entry point: wires config, keybinding registration and the
 * chat-typing event handlers.
 */
public class VietnameseTelexClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientHooks.register();
        VietnameseTelex.LOGGER.info("[VietnameseTelex] client init - builtin telex ready");
    }
}
