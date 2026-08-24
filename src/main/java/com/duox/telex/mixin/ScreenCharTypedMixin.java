package com.duox.telex.mixin;

import com.duox.telex.ClientHooks;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric API (screen-api v1) no longer exposes character-typed screen events,
 * so the chat Telex interception hooks into {@link Screen#charTyped} directly.
 * {@link ClientHooks} gates everything on an open, focused ChatScreen.
 *
 * <p>26.x replaced the old {@code charTyped(char, int)} signature with a
 * {@link CharacterEvent} record.</p>
 */
@Mixin(Screen.class)
public class ScreenCharTypedMixin {

    @Inject(method = "charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void vietnamesetelex$charTyped(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        char c = (char) event.codepoint();
        if (ClientHooks.onCharTyped((Screen) (Object) this, c)) {
            cir.setReturnValue(true);
        }
    }
}
