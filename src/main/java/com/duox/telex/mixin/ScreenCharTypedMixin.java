package com.duox.telex.mixin;

import com.duox.telex.ClientHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

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
 * <p>{@link Screen} itself never declares {@code charTyped} in Mojang mappings
 * (it is inherited), and interfaces cannot be mixin targets, hence the concrete
 * {@link EditBox} target.</p>
 *
 * <p>{@link ClientHooks} gates everything on an open ChatScreen with its
 * EditBox focused and the caret at the end.</p>
 */
@Mixin(EditBox.class)
public class ScreenCharTypedMixin {

    @Inject(method = "charTyped(CI)Z", at = @At("HEAD"), cancellable = true)
    private void vietnamesetelex$charTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ClientHooks.onCharTyped(Minecraft.getInstance().screen, codePoint)) {
            cir.setReturnValue(true);
        }
    }
}
