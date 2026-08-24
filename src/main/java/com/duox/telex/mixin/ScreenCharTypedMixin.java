package com.duox.telex.mixin;

import com.duox.telex.ClientHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric API (screen-api v1) no longer exposes character-typed screen events,
 * so the chat Telex interception hooks into {@link ContainerEventHandler#charTyped}
 * directly - in Mojang mappings {@code charTyped} is declared there, not on
 * {@link Screen} - and cancels it before vanilla forwards the character to the
 * focused widget.
 *
 * <p>{@link ClientHooks} gates everything on an open, focused ChatScreen.</p>
 */
@Mixin(ContainerEventHandler.class)
public class ScreenCharTypedMixin {

    @Inject(method = "charTyped(CI)Z", at = @At("HEAD"), cancellable = true)
    private void vietnamesetelex$charTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ClientHooks.onCharTyped(Minecraft.getInstance().screen, codePoint)) {
            cir.setReturnValue(true);
        }
    }
}
