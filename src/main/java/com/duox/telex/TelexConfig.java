package com.duox.telex;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side configuration for the builtin Telex input method.
 */
public final class TelexConfig {

    private TelexConfig() {
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable the builtin Vietnamese Telex converter while typing in chat")
            .define("enabled", true);

    public static final ModConfigSpec.BooleanValue SHOW_INDICATOR = BUILDER
            .comment("Show a small [TELEX] indicator in the top-left corner while the chat screen is open")
            .define("showIndicator", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
