package com.duox.telex.mixin;

import com.duox.telex.ClientHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric API (screen-api v1) no longer exposes character-typed screen events,
 * so the chat Telex interception hooks into {@link EditBox#charTyped} - the
 * concrete point where vanilla delivers characters to a focused text field -
 * and cancels it when {@link ClientHooks} consumes the character.
 *
 * <p>{@code Screen} itself never declares {@code charTyped} in Mojang mappings
 * (it is inherited), and interfaces cannot be mixin targets, hence the concrete
 * {@link EditBox} target.</p>
 *
 * <p>26.x replaced the old {@code charTyped(char, int)} signature with a
 * {@link CharacterEvent} record.</p>
 */
@Mixin(EditBox.class)
public class ScreenCharTypedMixin {

    @Inject(method = "charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void vietnamesetelex$charTyped(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        char c = (char) event.codepoint();
        if (ClientHooks.onCharTyped(Minecraft.getInstance().gui.screen(), c)) {
            cir.setReturnValue(true);
        }
    }
}
