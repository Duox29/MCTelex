package com.duox.telex;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side configuration for the builtin Telex input method.
 */
public final class TelexConfig {

    private TelexConfig() {
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable the builtin Vietnamese Telex converter while typing in chat")
            .define("enabled", true);

    public static final ForgeConfigSpec.BooleanValue SHOW_INDICATOR = BUILDER
            .comment("Show a small [TELEX] indicator in the top-left corner while the chat screen is open")
            .define("showIndicator", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
