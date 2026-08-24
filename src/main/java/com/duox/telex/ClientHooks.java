package com.duox.telex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

/**
 * Client-side glue between Minecraft's chat input and {@link TelexEngine} -
 * Fabric edition.
 *
 * <p>Fabric exposes screen input through per-screen events registered in
 * {@code ScreenEvents.BEFORE_INIT}; returning {@code false} from an "allow"
 * handler cancels the vanilla behaviour, equivalent to NeoForge's
 * cancellable events.</p>
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    public static final KeyMapping TOGGLE_TELEX = new KeyMapping(
            "key.vietnamesetelex.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyMapping.Category.MISC);

    /** Trailing run of ASCII letters in the edit box value (capturing group 1). */
    private static final Pattern TAIL_WORD = Pattern.compile("([A-Za-z]+)$");

    /** Raw telex letters typed so far for the word currently being composed. */
    private static String rawWord = "";
    /** Everything before the composing word. */
    private static String basePrefix = "";
    /** Full edit-box value the handler believes is currently shown. */
    private static String expected = "";

    // ------------------------------------------------------------------
    // Registration helpers
    // ------------------------------------------------------------------

    public static void register() {
        TelexConfig.load();
        KeyMappingHelper.registerKeyMapping(TOGGLE_TELEX);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_TELEX.consumeClick()) {
                toggle();
            }
        });

        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ChatScreen)) {
                return;
            }
            rawWord = "";
            basePrefix = "";
            expected = "";

            // Character input is intercepted via ScreenCharTypedMixin (Fabric API
            // no longer ships character-typed screen events).
            // Fabric allow-events: return true = let vanilla process the key,
            // false = cancel. onKeyPressed follows the same convention.
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, keyEvent) ->
                    onKeyPressed(scr, keyEvent));
            ScreenEvents.afterForeground(screen).register((scr, graphics, mouseX, mouseY, tickDelta) ->
                    onRender(graphics));
        });
    }

    // ------------------------------------------------------------------
    // Event handlers
    // ------------------------------------------------------------------

    /**
     * Called from {@code ScreenCharTypedMixin} for every screen character.
     *
     * @return true when the character was consumed (caller must cancel vanilla),
     *         false to let vanilla insert it normally
     */
    public static boolean onCharTyped(Screen screen, char c) {
        EditBox box = focusedChatBox(screen);
        if (box == null) {
            return false; // not ours - let vanilla type it
        }
        boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        if (!letter || !caretAtEnd(box)) {
            return false; // space, digits, punctuation, caret not at end: vanilla handles
        }
        syncIfChanged(box);
        rawWord += c;
        applyTo(box);
        return true; // consumed - vanilla must not insert it again
    }

    private static boolean onKeyPressed(Screen screen, net.minecraft.client.input.KeyEvent keyEvent) {
        int keyCode = keyEvent.key();
        // allow toggling even while the chat box is open
        if (keyCode != GLFW.GLFW_KEY_UNKNOWN && TOGGLE_TELEX.matches(keyEvent)
                && (keyEvent.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0) {
            toggle();
            return false; // consumed
        }

        EditBox box = focusedChatBox(screen);
        if (box == null || keyCode != GLFW.GLFW_KEY_BACKSPACE) {
            return true;
        }
        if ((keyEvent.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
            return true; // ctrl/alt+backspace: let vanilla handle (delete word etc.)
        }
        if (!caretAtEnd(box) || !box.getHighlighted().isEmpty() || rawWord.isEmpty()) {
            return true;
        }
        syncIfChanged(box);
        if (rawWord.isEmpty()) {
            return true;
        }
        rawWord = rawWord.substring(0, rawWord.length() - 1);
        applyTo(box);
        return false; // consumed
    }

    /** Small status tag in the top-left corner while chatting. */
    private static void onRender(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        if (!TelexConfig.isEnabled() || !TelexConfig.showIndicator()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Component text = Component.translatable("overlay.vietnamesetelex.indicator");
        graphics.text(mc.font, text, 6, 6, 0xFF55FFAA, true);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static void toggle() {
        boolean now = !TelexConfig.isEnabled();
        TelexConfig.setEnabled(now);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.translatable(
                    now ? "message.vietnamesetelex.enabled" : "message.vietnamesetelex.disabled"));
        }
    }

    private static EditBox focusedChatBox(Screen screen) {
        if (!TelexConfig.isEnabled() || !(screen instanceof ChatScreen chat)) {
            return null;
        }
        return chat.getFocused() instanceof EditBox box ? box : null;
    }

    private static boolean caretAtEnd(EditBox box) {
        return box.getCursorPosition() >= box.getValue().length();
    }

    /** Re-derives tracking state when the box was modified outside our control. */
    private static void syncIfChanged(EditBox box) {
        if (!box.getValue().equals(expected)) {
            try {
                resync(box);
            } catch (Exception e) {
                // fail-safe: never break typing - fall back to plain pass-through
                VietnameseTelex.LOGGER.error("[VietnameseTelex] resync failed; passing through literally", e);
                rawWord = "";
                basePrefix = box.getValue();
                expected = box.getValue();
            }
        }
    }

    private static void resync(EditBox box) {
        String value = box.getValue();
        expected = value;

        Matcher m = TAIL_WORD.matcher(value);
        if (m.find()) {
            String tail = m.group(1);
            // seed from pure-ASCII words so retro-toning still works ("chao" + s -> cháo)
            if (TelexEngine.convert(tail).equals(tail)) {
                rawWord = tail;
                basePrefix = value.substring(0, value.length() - tail.length());
                return;
            }
        }
        rawWord = "";
        basePrefix = value;
    }

    private static void applyTo(EditBox box) {
        String display = TelexEngine.convert(rawWord);
        String full = basePrefix + display;
        box.setValue(full);
        box.moveCursorToEnd(false);
        expected = full;
    }
}
