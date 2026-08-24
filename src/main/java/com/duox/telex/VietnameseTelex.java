package com.duox.telex;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * Shared constants. All the interesting work happens on the client -
 * see {@link VietnameseTelexClient}, {@link ClientHooks} and {@link TelexEngine}.
 */
public final class VietnameseTelex {
    public static final String MODID = "vietnamesetelex";
    public static final Logger LOGGER = LogUtils.getLogger();

    private VietnameseTelex() {
    }
}
