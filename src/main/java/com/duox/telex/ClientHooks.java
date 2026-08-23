package com.duox.telex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Client-side glue between Minecraft's chat input and {@link TelexEngine}.
 *
 * <p>Only active while a {@link ChatScreen} is open and its {@link EditBox} is
 * focused with the caret at the end - gameplay keys are never touched.</p>
 *
 * <p>The handler keeps the raw ASCII typed for the trailing word so that
 * backspace gracefully removes diacritics one keystroke at a time, exactly
 * like a desktop Telex implementation.</p>
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    private static final String CATEGORY = "key.categories.misc";

    public static final KeyMapping TOGGLE_TELEX = new KeyMapping(
            "key.vietnamesetelex.toggle",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            CATEGORY);

    /** Trailing run of ASCII letters in the edit box value (capturing group 1). */
    private static final Pattern TAIL_WORD = Pattern.compile("([A-Za-z]+)$");

    /** Raw telex letters typed so far for the word currently being composed. */
    private static String rawWord = "";
    /** Everything before the composing word. */
    private static String basePrefix = "";
    /** Full edit-box value the handler believes is currently shown. */
    private static String expected = "";

    // ------------------------------------------------------------------
    // Registration helpers (called from the client mod constructor)
    // ------------------------------------------------------------------

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_TELEX);
    }

    // ------------------------------------------------------------------
    // Event handlers
    // ------------------------------------------------------------------

    /** Any screen switch invalidates the composing word; state re-seeds lazily. */
    public static void onScreenOpening(ScreenEvent.Opening event) {
        rawWord = "";
        basePrefix = "";
        expected = "";
    }

    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        EditBox box = focusedChatBox(event);
        if (box == null) {
            return;
        }
        char c = event.getCodePoint();
        boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        if (!letter || !caretAtEnd(box)) {
            return; // pass straight through to vanilla
        }
        syncIfChanged(box);
        event.setCanceled(true);
        rawWord += c;
        applyTo(box);
    }

    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        // allow toggling even while the chat box is open
        if (TOGGLE_TELEX.matches(event.getKeyCode(), event.getScanCode())
                && (event.getModifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0) {
            toggle();
            event.setCanceled(true);
            return;
        }

        EditBox box = focusedChatBox(event);
        if (box == null || event.getKeyCode() != GLFW.GLFW_KEY_BACKSPACE) {
            return;
        }
        if ((event.getModifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
            return; // ctrl/alt+backspace: let vanilla handle (delete word etc.)
        }
        if (!caretAtEnd(box) || !box.getHighlighted().isEmpty() || rawWord.isEmpty()) {
            return;
        }
        syncIfChanged(box);
        if (rawWord.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        rawWord = rawWord.substring(0, rawWord.length() - 1);
        applyTo(box);
    }

    /** Toggle hotkey pressed outside any screen. */
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return; // handled by ScreenEvent.KeyPressed.Pre while chatting
        }
        if (TOGGLE_TELEX.matches(event.getKey(), event.getScanCode())) {
            toggle();
        }
    }

    /** Small status tag in the top-left corner while chatting. */
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen)) {
            return;
        }
        if (!TelexConfig.ENABLED.get() || !TelexConfig.SHOW_INDICATOR.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Component text = Component.translatable("overlay.vietnamesetelex.indicator");
        event.getGuiGraphics().drawString(mc.font, text, 6, 6, 0xFF55FFAA, true);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static void toggle() {
        boolean now = !TelexConfig.ENABLED.get();
        TelexConfig.ENABLED.set(now);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(
                    now ? "message.vietnamesetelex.enabled" : "message.vietnamesetelex.disabled"), true);
        }
    }

    private static EditBox focusedChatBox(ScreenEvent event) {
        if (!TelexConfig.ENABLED.get() || !(event.getScreen() instanceof ChatScreen chat)) {
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
