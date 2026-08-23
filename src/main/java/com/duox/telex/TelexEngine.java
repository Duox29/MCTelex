package com.duox.telex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure-Java Telex to Vietnamese converter for a single word (letters only, no spaces).
 *
 * <p>The engine keeps no state: the caller feeds the raw ASCII typed so far for the
 * current word and gets back the display form. Because the raw buffer is kept by the
 * caller, backspacing one character automatically "un-types" the last action
 * (e.g. raw "duongs" renders as duong-with-sac, one backspace renders "duong"),
 * exactly like a real Telex input method.</p>
 *
 * <p>Rules implemented:
 * <ul>
 *   <li>Tone keys (anywhere after the onset): s sac, f huyen, r hoi, x nga, j nang.</li>
 *   <li>Vowel keys: aa -> â, aw -> ă, ee -> ê, oo -> ô, ow -> o+horn, uw -> u+horn,
 *       leading w -> u+horn, dd -> đ.</li>
 *   <li>Glide spellings accepted: "uo" = uong-glide ("duong" -> d-u-o-horn),
 *       "ie"/"ye" = i-circ/y-circ ("dien" -> dien-with-circ).</li>
 *   <li>Syllable validation against ~170 Vietnamese rhymes so English words such as
 *       "hello" or "english" pass through untouched, while retro-active tones still
 *       work ("chao" + "s" -> chao-with-sac, "muon" -> muon-with-hoi-on-o).</li>
 * </ul></p>
 */
public final class TelexEngine {

    private TelexEngine() {
    }

    /** Marks indexed [base vowel][tone], tone 1..5 = huyen, sac, hoi, nga, nang. */
    private static final Map<Character, String[]> MARKS = new HashMap<>();

    static {
        put("a", "àáảãạ");
        put("ă", "ằắẳẵặ");
        put("â", "ầấẩẫậ");
        put("e", "èéẻẽẹ");
        put("ê", "ềếểễệ");
        put("i", "ìíỉĩị");
        put("o", "òóỏõọ");
        put("ô", "ồốổỗộ");
        put("ơ", "ờớởỡợ");
        put("u", "ùúủũụ");
        put("ư", "ừứửữự");
        put("y", "ỳýỷỹỵ");
    }

    private static void put(String base, String marks) {
        String[] t = new String[6];
        t[0] = base;
        for (int i = 0; i < 5; i++) {
            t[i + 1] = String.valueOf(marks.charAt(i));
        }
        MARKS.put(base.charAt(0), t);
    }

    /** s sac, f huyen, r hoi, x nga, j nang (index into MARKS tone 2,1,3,4,5). */
    private static final String TONE_KEYS = "sfrxj";
    private static final int[] TONE_INDEX = {2, 1, 3, 4, 5};

    /**
     * Whole-word raw rewrites for frequent lexical ambiguities that pure
     * syllable structure cannot resolve. Applied to the lowercased word before
     * parsing, e.g. "khong" would otherwise stay literal because kh+ong is a
     * well-formed parse, while every Vietnamese speaker means không.
     */
    private static final Map<String, String> RAW_OVERRIDES = Map.of(
            "khong", "khoong");

    private static final Set<Character> VOWELS =
            Set.of('a', 'ă', 'â', 'e', 'ê', 'i', 'o', 'ô', 'ơ', 'u', 'ư', 'y');

    // ------------------------------------------------------------------
    // Rhyme table
    // ------------------------------------------------------------------

    /** A rhyme: display spelling, index of the tone-bearing char, accepted raw ASCII forms. */
    private record Rhyme(String display, int anchor, List<String> raws) {
    }

    private static final List<Rhyme> RHYMES = new ArrayList<>();
    private static final Map<String, Rhyme> BY_RAW = new HashMap<>();

    private static void rhyme(String display) {
        int anchor = anchorOf(display);
        List<String> raws = rawForms(display);
        Rhyme r = new Rhyme(display, anchor, raws);
        RHYMES.add(r);
        for (String raw : raws) {
            BY_RAW.putIfAbsent(raw, r);
        }
    }

    static {
        // zero coda
        for (String v : new String[]{
                "a", "ă", "â", "e", "ê", "i", "y", "o", "ô", "ơ", "u", "ư",
                "oa", "oe", "uy", "ia", "ya", "ua", "ưa", "uya"}) {
            rhyme(v);
        }
        // coda i / y
        for (String v : new String[]{
                "ai", "ay", "ây", "oi", "ôi", "ơi", "ui", "ưi", "uôi", "ươi",
                "oai", "oay", "uay"}) {
            rhyme(v);
        }
        // coda u / o
        for (String v : new String[]{
                "ao", "au", "âu", "eo", "êu", "iu", "iêu", "yêu", "ưu", "ươu"}) {
            rhyme(v);
        }
        // coda m
        for (String v : new String[]{
                "am", "ăm", "âm", "em", "êm", "im", "om", "ôm", "ơm", "um",
                "iêm", "yêm", "ươm"}) {
            rhyme(v);
        }
        // coda n
        for (String v : new String[]{
                "an", "ăn", "ân", "en", "ên", "in", "on", "ôn", "ơn", "un",
                "ươn", "iên", "yên", "uan", "uân", "oan", "oân", "oăn", "uyên"}) {
            rhyme(v);
        }
        // coda ng / nh
        for (String v : new String[]{
                "ang", "ăng", "âng", "anh", "inh", "ênh", "ong", "ông", "ung",
                "ưng", "ương", "iêng", "oang", "oâng", "uâng", "uanh"}) {
            rhyme(v);
        }
        // codas c / ch / t / p
        for (String v : new String[]{
                "ac", "ăc", "âc", "ach", "at", "ăt", "ât", "ap", "ăp", "âp",
                "ec", "êc", "êch", "êt", "êp",
                "ic", "ich", "it", "ip",
                "oc", "ot", "op", "ôc", "ôt", "ôp",
                "uc", "ut", "up", "ưc", "ưt", "ưp",
                "iêt", "iêp", "yêt",
                "uôc", "uôt",
                "ươc", "ươt", "ươp",
                "oat", "oac",
                "uat", "uât", "uêt", "uyêt"}) {
            rhyme(v);
        }
    }

    /** Index of the tone-bearing character inside the display spelling of a rhyme. */
    private static int anchorOf(String d) {
        if (d.startsWith("ươi") || d.startsWith("ươ") || d.startsWith("iê") || d.startsWith("yê")) {
            return 1;
        }
        if (d.startsWith("uô") || d.startsWith("uâ")
                || d.startsWith("oa") || d.startsWith("oe") || d.startsWith("oy")
                || d.startsWith("uai") || d.startsWith("oai") || d.startsWith("uay")
                || d.startsWith("oay") || d.startsWith("uy") || d.startsWith("uya")) {
            return 1;
        }
        return 0;
    }

    /** ASCII forms that type the given display rhyme. */
    private static List<String> rawForms(String d) {
        String coda;
        if (d.startsWith("ươ")) { // uong-family: duong / duwong / duwowng
            coda = d.substring(2);
            return List.of("uo" + coda, "uwo" + coda, "uwow" + coda);
        }
        if (d.startsWith("uyê")) { // uyên: thuyen / thuyeen
            coda = d.substring(3);
            return List.of("uye" + coda, "uyee" + coda);
        }
        if (d.startsWith("iê")) { // iên: dien / dieen
            coda = d.substring(2);
            return List.of("ie" + coda, "iee" + coda);
        }
        if (d.startsWith("yê")) { // yên: yen / yeen
            coda = d.substring(2);
            return List.of("ye" + coda, "yee" + coda);
        }
        if (d.startsWith("uô")) { // uôi/uôn/... explicit oo only (bare uo belongs to uong-family)
            coda = d.substring(2);
            return List.of("uoo" + coda);
        }
        // standard per-character expansion
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            sb.append(simpleRaw(d.charAt(i)));
        }
        return List.of(sb.toString());
    }

    private static String simpleRaw(char c) {
        return switch (c) {
            case 'ă' -> "aw";
            case 'â' -> "aa";
            case 'ê' -> "ee";
            case 'ô' -> "oo";
            case 'ơ' -> "ow";
            case 'ư' -> "uw";
            default -> String.valueOf(c);
        };
    }

    // ------------------------------------------------------------------
    // Onsets
    // ------------------------------------------------------------------

    /** Raw onsets ordered longest-first; "" always matches. */
    private static final String[] ONSETS = {
            "ngh",
            "ch", "gh", "gi", "kh", "nh", "ph", "th", "tr", "ng", "dd",
            "b", "c", "d", "g", "h", "k", "l", "m", "n", "p", "q", "r",
            "s", "t", "v", "x", "w", ""
    };

    private static String onsetDisplay(String rawOnset) {
        if (rawOnset.equals("dd")) {
            return "đ";
        }
        if (rawOnset.equals("w")) {
            return "ư";
        }
        return rawOnset;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Converts one word of raw telex text to its Vietnamese display form.
     * Returns the input unchanged when it cannot be parsed as (part of) a
     * Vietnamese syllable.
     */
    public static String convert(String word) {
        if (word == null || word.isEmpty()) {
            return word;
        }
        String lower = word.toLowerCase(Locale.ROOT);
        lower = RAW_OVERRIDES.getOrDefault(lower, lower);

        String bestOnset = null;
        Match best = null;

        for (String onset : ONSETS) {
            if (!lower.startsWith(onset)) {
                continue;
            }
            String rest = lower.substring(onset.length());
            Match m = matchRhyme(rest);
            if (m == null) {
                continue;
            }
            if (m.exact()) {
                // first exact match wins (longest onset tried first)
                return render(word, onset, m);
            }
            if (best == null) {
                best = m;
                bestOnset = onset;
            }
        }
        if (best != null) {
            return render(word, bestOnset, best);
        }
        return word; // not Vietnamese-shaped: keep literal
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    private record Match(Rhyme rhyme, boolean exact, Character tone, String restAfterTone) {
    }

    /**
     * Tries to interpret rest (the part after the onset) as a rhyme,
     * optionally with one tone key inserted anywhere in it.
     */
    private static Match matchRhyme(String rest) {
        if (rest.isEmpty()) {
            // bare onset while typing ("d", "ch", ...) - valid partial
            return new Match(null, false, null, "");
        }

        Rhyme exact = BY_RAW.get(rest);
        if (exact != null) {
            return new Match(exact, true, null, rest);
        }

        // tone key extraction: remove one occurrence and retry
        for (int t = 0; t < TONE_KEYS.length(); t++) {
            char tc = TONE_KEYS.charAt(t);
            int idx = rest.indexOf(tc);
            if (idx < 0) {
                continue;
            }
            String stripped = rest.substring(0, idx) + rest.substring(idx + 1);
            Rhyme exactToned = BY_RAW.get(stripped);
            if (exactToned != null) {
                return new Match(exactToned, true, tc, stripped);
            }
            if (startsWithAnyRaw(stripped)) {
                return new Match(null, false, tc, stripped);
            }
        }

        if (startsWithAnyRaw(rest)) {
            return new Match(null, false, null, rest);
        }
        return null;
    }

    private static boolean startsWithAnyRaw(String s) {
        for (Rhyme r : RHYMES) {
            for (String raw : r.raws()) {
                if (raw.startsWith(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private static String render(String original, String onset, Match m) {
        StringBuilder sb = new StringBuilder(onsetDisplay(onset));
        if (m.rhyme() != null) {
            sb.append(toned(m.rhyme().display(), m.rhyme().anchor(), m.tone()));
        } else {
            // partial: map what we have so far
            String partial = partialMap(m.restAfterTone());
            if (m.tone() != null) {
                if (!placeTone(partial, m.tone(), sb)) {
                    // could not place the tone on the partial: show everything literally
                    sb.append(partialMap(m.restAfterTone() + m.tone()));
                }
            } else {
                sb.append(partial);
            }
        }
        return applyCase(original, sb.toString());
    }

    /** Applies a tone mark to the anchored vowel of a complete rhyme display. */
    private static String toned(String display, int anchor, Character tone) {
        if (tone == null || display.isEmpty()) {
            return display;
        }
        char base = display.charAt(Math.min(anchor, display.length() - 1));
        String[] marks = MARKS.get(base);
        if (marks == null) {
            return display;
        }
        int ti = TONE_INDEX[TONE_KEYS.indexOf(tone)];
        return display.substring(0, anchor) + marks[ti] + display.substring(anchor + 1);
    }

    /** Greedy digraph mapping of an incomplete raw tail ("uo"->uong-glide, "aw"->a-breve, ...). */
    private static String partialMap(String raw) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            if (i + 1 < raw.length()) {
                String two = raw.substring(i, i + 2);
                String mapped = switch (two) {
                    case "aa" -> "â";
                    case "aw" -> "ă";
                    case "ee" -> "ê";
                    case "oo" -> "ô";
                    case "ow" -> "ơ";
                    case "uw" -> "ư";
                    case "uo" -> "ươ";
                    default -> null;
                };
                if (mapped != null) {
                    sb.append(mapped);
                    i += 2;
                    continue;
                }
            }
            sb.append(raw.charAt(i));
            i++;
        }
        return sb.toString();
    }

    /** Inserts a tone mark on the first vowel of partial; returns false when impossible. */
    private static boolean placeTone(String partial, Character tone, StringBuilder out) {
        for (int i = 0; i < partial.length(); i++) {
            char c = partial.charAt(i);
            String[] marks = MARKS.get(c);
            if (marks != null) {
                int ti = TONE_INDEX[TONE_KEYS.indexOf(tone)];
                out.append(partial, 0, i).append(marks[ti]).append(partial.substring(i + 1));
                return true;
            }
        }
        return false;
    }

    /** Copies the case pattern of the typed word onto the rendered display. */
    private static String applyCase(String original, String displayLower) {
        String lower = original.toLowerCase(Locale.ROOT);
        if (original.equals(lower)) {
            return displayLower;
        }
        String upper = original.toUpperCase(Locale.ROOT);
        if (original.equals(upper)) {
            return displayLower.toUpperCase(Locale.ROOT);
        }
        // capitalized or mixed: uppercase the first letter
        StringBuilder sb = new StringBuilder(displayLower);
        for (int i = 0; i < sb.length(); i++) {
            if (Character.isLetter(sb.charAt(i))) {
                sb.setCharAt(i, Character.toUpperCase(sb.charAt(i)));
                break;
            }
        }
        return sb.toString();
    }
}
