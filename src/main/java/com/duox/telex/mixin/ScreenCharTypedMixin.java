package com.duox.telex.mixin;

import com.duox.telex.ClientHooks;

import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric API (screen-api v1) no longer exposes character-typed screen events,
 * so the chat Telex interception hooks into {@link Screen#charTyped} directly.
 * {@link ClientHooks} gates everything on an open, focused ChatScreen.
 */
@Mixin(Screen.class)
public class ScreenCharTypedMixin {

    @Inject(method = "charTyped(CI)Z", at = @At("HEAD"), cancellable = true)
    private void vietnamesetelex$charTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ClientHooks.onCharTyped((Screen) (Object) this, codePoint)) {
            cir.setReturnValue(true);
        }
    }
}
