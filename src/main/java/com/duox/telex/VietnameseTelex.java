package com.duox.telex;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Common entry point. All the interesting work happens on the client -
 * see {@link VietnameseTelexClient}, {@link ClientHooks} and {@link TelexEngine}.
 */
@Mod(VietnameseTelex.MODID)
public class VietnameseTelex {
    public static final String MODID = "vietnamesetelex";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VietnameseTelex(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[VietnameseTelex] common init");
    }
}
