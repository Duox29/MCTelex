package com.duox.telex;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Common entry point. All the interesting work happens on the client -
 * see {@link VietnameseTelexClient}, {@link ClientHooks} and {@link TelexEngine}.
 */
@Mod(VietnameseTelex.MODID)
public class VietnameseTelex {
    public static final String MODID = "vietnamesetelex";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VietnameseTelex() {
        LOGGER.info("[VietnameseTelex] common init");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new VietnameseTelexClient(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
