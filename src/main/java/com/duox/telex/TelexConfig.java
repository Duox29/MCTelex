package com.duox.telex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side configuration for the builtin Telex input method.
 *
 * <p>Fabric has no built-in config system, so this is a simple JSON file in
 * the standard config directory. Toggle state is persisted on change.</p>
 */
public final class TelexConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("vietnamesetelex.json");

    private static boolean enabled = true;
    private static boolean showIndicator = true;

    private TelexConfig() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static boolean showIndicator() {
        return showIndicator;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                Data d = GSON.fromJson(Files.readString(PATH), Data.class);
                if (d != null) {
                    enabled = d.enabled;
                    showIndicator = d.showIndicator;
                }
            }
        } catch (Exception e) {
            VietnameseTelex.LOGGER.error("[VietnameseTelex] failed to read config, using defaults", e);
        }
    }

    private static void save() {
        try {
            Data d = new Data();
            d.enabled = enabled;
            d.showIndicator = showIndicator;
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(d));
        } catch (IOException e) {
            VietnameseTelex.LOGGER.error("[VietnameseTelex] failed to write config", e);
        }
    }

    private static class Data {
        boolean enabled = true;
        boolean showIndicator = true;
    }
}
