package com.duox.telex;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Java port of the OpenKey Vietnamese typing engine (Sources/OpenKey/engine),
 * reduced to Telex + precomposed Unicode output.
 *
 * <p>Like OpenKey, the engine keeps a word buffer in which every entry is a key
 * code decorated with flags:</p>
 * <ul>
 *   <li>{@link #CAPS} - typed with shift</li>
 *   <li>{@link #TONE} - circumflex (^): a->â e->ê o->ô, d->đ</li>
 *   <li>{@link #TONEW} - horn/breve (w): a->ă o->ơ u->ư, standalone w->ư</li>
 *   <li>{@link #M1}..{@link #M5} - sắc huyền hỏi ngã nặng</li>
 *   <li>{@link #STANDALONE} - the ư originated from a lone "w"</li>
 * </ul>
 *
 * <p>Each key is matched incrementally against OpenKey's pattern tables
 * ({@code _vowel}, {@code _vowelForMark}, {@code _consonantD}, ...) and after
 * every key {@link #checkGrammar()} re-places marks onto the correct vowel, so
 * modifiers may arrive in any order ("nguowfi" -> người, "chuiwr" -> chửi).</p>
 *
 * <p>Mod-specific additions on top of OpenKey (see {@link #normalize}):</p>
 * <ul>
 *   <li>"khong" -> "khoong" (pure structure cannot know this one)</li>
 *   <li>bare glide spellings get an implicit trailing w / doubled e:
 *       "duong" -> dương, "nguoi" -> người, "dien" -> diên</li>
 * </ul>
 */
public final class TelexEngine {

    private TelexEngine() {
    }

    // ------------------------------------------------------------------
    // Flags (bit layout mirrors OpenKey DataType.h)
    // ------------------------------------------------------------------

    private static final int CAPS = 1 << 16;
    private static final int TONE = 1 << 17;
    private static final int TONEW = 1 << 18;
    private static final int M1 = 1 << 19; // sắc
    private static final int M2 = 1 << 20; // huyền
    private static final int M3 = 1 << 21; // hỏi
    private static final int M4 = 1 << 22; // ngã
    private static final int M5 = 1 << 23; // nặng
    private static final int MARK_MASK = M1 | M2 | M3 | M4 | M5;
    private static final int STANDALONE = 1 << 24;

    private static final int MAX_BUFF = 32;

    /** Tone diacritics indexed [base vowel][1=sắc 2=huyền 3=hỏi 4=ngã 5=nặng]. */
    private static final Map<Character, String[]> MARKS = new HashMap<>();

    static {
        put("a", "áàảãạ");
        put("ă", "ắằẳẵặ");
        put("â", "ấầẩẫậ");
        put("e", "éèẻẽẹ");
        put("ê", "ếềểễệ");
        put("i", "íìỉĩị");
        put("o", "óòỏõọ");
        put("ô", "ốồổỗộ");
        put("ơ", "ớờởỡợ");
        put("u", "úùủũụ");
        put("ư", "ứừửữự");
        put("y", "ýỳỷỹỵ");
    }

    private static void put(String base, String marks) {
        String[] t = new String[6];
        t[0] = base;
        for (int i = 0; i < 5; i++) {
            t[i + 1] = String.valueOf(marks.charAt(i));
        }
        MARKS.put(base.charAt(0), t);
    }

    // ------------------------------------------------------------------
    // Pattern tables (ported from Vietnamese.cpp)
    // ------------------------------------------------------------------

    private static int k(char c) {
        return c;
    }

    /** _vowel: trigger key -> list of tail patterns (matched against word suffix). */
    private static final Map<Character, int[][]> VOWEL = new HashMap<>();
    /** _vowelForMark: mark placement candidate tails. */
    private static final Map<Character, int[][]> VOWEL_FOR_MARK = new HashMap<>();
    /** _vowelCombine: {canHaveEndConsonant, elems...}; elems may carry TONE/TONEW requirement. */
    private static final Map<Character, int[][]> VOWEL_COMBINE = new HashMap<>();
    /** _consonantD: rhymes that turn a leading d into đ. */
    private static final int[][] CONSONANT_D;
    /** _consonantTable: valid onsets. */
    private static final int[][] CONSONANT_TABLE;
    /** _endConsonantTable: valid ending consonants. */
    private static final int[][] END_CONSONANT_TABLE;
    /** _standaloneWbad: single characters after which w stays literal. */
    private static final char[] STANDALONE_W_BAD = {'w', 'e', 'y', 'f', 'j', 'k', 'z'};
    /** _doubleWAllowed: two-letter onsets after which a lone w becomes ư. */
    private static final char[][] DOUBLE_W_ALLOWED = {
            {'t', 'r'}, {'t', 'h'}, {'c', 'h'}, {'n', 'h'}, {'n', 'g'},
            {'k', 'h'}, {'g', 'i'}, {'p', 'h'}, {'g', 'h'},
    };

    private static void putPatterns(Map<Character, int[][]> map, char key, int[][] patterns) {
        map.put(key, patterns);
    }

    static {
        // ---- _vowel ----
        putPatterns(VOWEL, 'a', new int[][]{
                {k('a'), k('n'), k('g')}, {k('a'), k('g')},
                {k('a'), k('n')}, {k('a'), k('m')}, {k('a'), k('u')},
                {k('a'), k('y')}, {k('a'), k('t')}, {k('a'), k('p')},
                {k('a')}, {k('a'), k('c')},
        });
        putPatterns(VOWEL, 'o', new int[][]{
                {k('o'), k('n'), k('g')}, {k('o'), k('g')},
                {k('o'), k('n')}, {k('o'), k('m')}, {k('o'), k('i')},
                {k('o'), k('c')}, {k('o'), k('t')}, {k('o'), k('p')},
                {k('o')},
        });
        putPatterns(VOWEL, 'e', new int[][]{
                {k('e'), k('n'), k('h')}, {k('e'), k('h')},
                {k('e'), k('n'), k('g')}, {k('e'), k('g')},
                {k('e'), k('c'), k('h')}, {k('e'), k('k')},
                {k('e'), k('c')}, {k('e'), k('t')}, {k('e'), k('y')},
                {k('e'), k('u')}, {k('e'), k('p')},
                {k('e'), k('c')}, {k('e'), k('n')}, {k('e'), k('m')},
                {k('e')},
        });
        putPatterns(VOWEL, 'w', new int[][]{
                {k('o'), k('n')},
                {k('u'), k('o'), k('n'), k('g')}, {k('u'), k('o'), k('g')},
                {k('u'), k('o'), k('n')},
                {k('u'), k('o'), k('i')},
                {k('u'), k('o'), k('c')},
                {k('o'), k('i')}, {k('o'), k('p')}, {k('o'), k('m')},
                {k('o'), k('a')}, {k('o'), k('t')},
                {k('u'), k('n'), k('g')}, {k('u'), k('g')},
                {k('a'), k('n'), k('g')}, {k('a'), k('g')},
                {k('u'), k('n')}, {k('u'), k('m')}, {k('u'), k('c')},
                {k('u'), k('a')}, {k('u'), k('i')}, {k('u'), k('t')},
                {k('u')},
                {k('a'), k('p')}, {k('a'), k('t')}, {k('a'), k('m')},
                {k('a'), k('n')}, {k('a')}, {k('a'), k('c')},
                {k('a'), k('c'), k('h')}, {k('a'), k('k')},
                {k('o')}, {k('u'), k('u')},
        });

        // ---- _vowelCombine ----
        putPatterns(VOWEL_COMBINE, 'a', new int[][]{
                {0, k('a'), k('i')}, {0, k('a'), k('o')}, {0, k('a'), k('u')},
                {0, k('a') | TONE, k('u')}, {0, k('a'), k('y')}, {0, k('a') | TONE, k('y')},
        });
        putPatterns(VOWEL_COMBINE, 'e', new int[][]{
                {0, k('e'), k('o')}, {0, k('e') | TONE, k('u')},
        });
        putPatterns(VOWEL_COMBINE, 'i', new int[][]{
                {1, k('i'), k('e') | TONE, k('u')}, {0, k('i'), k('a')},
                {1, k('i'), k('e') | TONE}, {0, k('i'), k('u')},
        });
        putPatterns(VOWEL_COMBINE, 'o', new int[][]{
                {0, k('o'), k('a'), k('i')}, {0, k('o'), k('a'), k('o')},
                {0, k('o'), k('a'), k('y')}, {0, k('o'), k('e'), k('o')},
                {1, k('o'), k('a')}, {1, k('o'), k('a') | TONEW},
                {1, k('o'), k('e')}, {0, k('o'), k('i')},
                {0, k('o') | TONE, k('i')}, {0, k('o') | TONEW, k('i')},
                {1, k('o'), k('o')}, {1, k('o') | TONE, k('o') | TONE},
        });
        putPatterns(VOWEL_COMBINE, 'u', new int[][]{
                {0, k('u'), k('y'), k('u')}, {1, k('u'), k('y'), k('e') | TONE},
                {0, k('u'), k('y'), k('a')},
                {0, k('u') | TONEW, k('o') | TONEW, k('u')},
                {0, k('u') | TONEW, k('o') | TONEW, k('i')},
                {0, k('u'), k('o') | TONE, k('i')},
                {0, k('u'), k('a') | TONE, k('y')},
                {1, k('u'), k('a'), k('o')}, {1, k('u'), k('a')},
                {1, k('u'), k('a') | TONEW}, {1, k('u'), k('a') | TONE},
                {0, k('u') | TONEW, k('a')},
                {1, k('u'), k('e') | TONE}, {0, k('u'), k('i')},
                {0, k('u') | TONEW, k('i')}, {1, k('u'), k('o')},
                {1, k('u'), k('o') | TONE}, {0, k('u'), k('o') | TONEW},
                {1, k('u') | TONEW, k('o') | TONEW}, {0, k('u') | TONEW, k('u')},
                {1, k('u'), k('y')},
        });
        putPatterns(VOWEL_COMBINE, 'y', new int[][]{
                {0, k('y'), k('e') | TONE, k('u')}, {1, k('y'), k('e') | TONE},
        });

        // ---- _consonantD ----
        CONSONANT_D = new int[][]{
                {k('d'), k('e'), k('n'), k('h')}, {k('d'), k('e'), k('h')},
                {k('d'), k('e'), k('n'), k('g')}, {k('d'), k('e'), k('g')},
                {k('d'), k('e'), k('c'), k('h')}, {k('d'), k('e'), k('k')},
                {k('d'), k('e'), k('n')}, {k('d'), k('e'), k('c')},
                {k('d'), k('e'), k('m')}, {k('d'), k('e')},
                {k('d'), k('e'), k('t')}, {k('d'), k('e'), k('u')},
                {k('d'), k('e'), k('o')}, {k('d'), k('e'), k('p')},
                {k('d'), k('u'), k('n'), k('g')}, {k('d'), k('u'), k('g')},
                {k('d'), k('u'), k('n')}, {k('d'), k('u'), k('m')},
                {k('d'), k('u'), k('c')}, {k('d'), k('u'), k('o')},
                {k('d'), k('u'), k('a')}, {k('d'), k('u'), k('o'), k('i')},
                {k('d'), k('u'), k('o'), k('c')}, {k('d'), k('u'), k('o'), k('n')},
                {k('d'), k('u'), k('o'), k('n'), k('g')}, {k('d'), k('u'), k('o'), k('g')},
                {k('d'), k('u')}, {k('d'), k('u'), k('p')}, {k('d'), k('u'), k('t')},
                {k('d'), k('u'), k('i')},
                {k('d'), k('i'), k('c'), k('h')}, {k('d'), k('i'), k('k')},
                {k('d'), k('i'), k('c')},
                {k('d'), k('i'), k('n'), k('h')}, {k('d'), k('i'), k('h')},
                {k('d'), k('i'), k('n')}, {k('d'), k('i')},
                {k('d'), k('i'), k('a')}, {k('d'), k('i'), k('e')},
                {k('d'), k('i'), k('e'), k('c')}, {k('d'), k('i'), k('e'), k('u')},
                {k('d'), k('i'), k('e'), k('n')}, {k('d'), k('i'), k('e'), k('m')},
                {k('d'), k('i'), k('e'), k('p')}, {k('d'), k('i'), k('t')},
                {k('d'), k('o')}, {k('d'), k('o'), k('a')},
                {k('d'), k('o'), k('a'), k('n')},
                {k('d'), k('o'), k('a'), k('n'), k('g')}, {k('d'), k('o'), k('a'), k('g')},
                {k('d'), k('o'), k('a'), k('n'), k('h')}, {k('d'), k('o'), k('a'), k('h')},
                {k('d'), k('o'), k('a'), k('m')}, {k('d'), k('o'), k('e')},
                {k('d'), k('o'), k('i')}, {k('d'), k('o'), k('p')},
                {k('d'), k('o'), k('c')}, {k('d'), k('o'), k('n')},
                {k('d'), k('o'), k('n'), k('g')}, {k('d'), k('o'), k('g')},
                {k('d'), k('o'), k('m')}, {k('d'), k('o'), k('t')},
                {k('d'), k('a')}, {k('d'), k('a'), k('t')}, {k('d'), k('a'), k('y')},
                {k('d'), k('a'), k('u')}, {k('d'), k('a'), k('i')}, {k('d'), k('a'), k('o')},
                {k('d'), k('a'), k('p')}, {k('d'), k('a'), k('c')},
                {k('d'), k('a'), k('c'), k('h')}, {k('d'), k('a'), k('k')},
                {k('d'), k('a'), k('n')},
                {k('d'), k('a'), k('n'), k('h')}, {k('d'), k('a'), k('h')},
                {k('d'), k('a'), k('n'), k('g')}, {k('d'), k('a'), k('g')},
                {k('d'), k('a'), k('m')},
                {k('d')},
        };

        // ---- _vowelForMark ----
        putPatterns(VOWEL_FOR_MARK, 'a', new int[][]{
                {k('a'), k('n'), k('g')}, {k('a'), k('g')},
                {k('a'), k('n')},
                {k('a'), k('n'), k('h')}, {k('a'), k('h')},
                {k('a'), k('m')}, {k('a'), k('u')}, {k('a'), k('y')},
                {k('a'), k('t')}, {k('a'), k('p')}, {k('a')}, {k('a'), k('c')},
                {k('a'), k('i')}, {k('a'), k('o')},
                {k('a'), k('c'), k('h')}, {k('a'), k('k')},
        });
        putPatterns(VOWEL_FOR_MARK, 'o', new int[][]{
                {k('o'), k('o'), k('n'), k('g')}, {k('o'), k('o'), k('g')},
                {k('o'), k('n'), k('g')}, {k('o'), k('g')},
                {k('o'), k('o'), k('n')}, {k('o'), k('o'), k('c')},
                {k('o'), k('o')},
                {k('o'), k('n')}, {k('o'), k('m')}, {k('o'), k('i')},
                {k('o'), k('c')}, {k('o'), k('t')}, {k('o'), k('p')},
                {k('o')},
        });
        putPatterns(VOWEL_FOR_MARK, 'e', new int[][]{
                {k('e'), k('n'), k('h')}, {k('e'), k('h')},
                {k('e'), k('n'), k('g')}, {k('e'), k('g')},
                {k('e'), k('c'), k('h')}, {k('e'), k('k')},
                {k('e'), k('c')}, {k('e'), k('t')}, {k('e'), k('y')},
                {k('e'), k('u')}, {k('e'), k('p')},
                {k('e'), k('c')}, {k('e'), k('n')}, {k('e'), k('m')},
                {k('e')},
        });
        putPatterns(VOWEL_FOR_MARK, 'i', new int[][]{
                {k('i'), k('n'), k('h')}, {k('i'), k('h')},
                {k('i'), k('c'), k('h')}, {k('i'), k('k')},
                {k('i'), k('n')}, {k('i'), k('t')}, {k('i'), k('u')},
                {k('i'), k('u'), k('p')},
                {k('i'), k('n')}, {k('i'), k('m')}, {k('i'), k('p')},
                {k('i'), k('a')}, {k('i'), k('c')},
                {k('i')},
        });
        putPatterns(VOWEL_FOR_MARK, 'u', new int[][]{
                {k('u'), k('n'), k('g')}, {k('u'), k('g')},
                {k('u'), k('i')}, {k('u'), k('o')}, {k('u'), k('y')},
                {k('u'), k('y'), k('n')}, {k('u'), k('y'), k('t')},
                {k('u'), k('y'), k('p')},
                {k('u'), k('y'), k('c'), k('h')}, {k('u'), k('y'), k('k')},
                {k('u'), k('y'), k('n'), k('h')}, {k('u'), k('y'), k('h')},
                {k('u'), k('t')}, {k('u'), k('u')}, {k('u'), k('a')},
                {k('u'), k('i')}, {k('u'), k('c')}, {k('u'), k('n')},
                {k('u'), k('m')}, {k('u'), k('p')},
                {k('u')},
        });
        putPatterns(VOWEL_FOR_MARK, 'y', new int[][]{
                {k('y')},
        });

        // ---- _consonantTable ----
        CONSONANT_TABLE = new int[][]{
                {k('n'), k('g'), k('h')},
                {k('p'), k('h')}, {k('t'), k('h')}, {k('t'), k('r')},
                {k('g'), k('i')}, {k('c'), k('h')}, {k('n'), k('h')},
                {k('n'), k('g')}, {k('k'), k('h')}, {k('g'), k('h')},
                {k('g')}, {k('c')}, {k('q')}, {k('k')}, {k('t')}, {k('r')},
                {k('h')}, {k('b')}, {k('m')}, {k('v')}, {k('n')}, {k('l')},
                {k('x')}, {k('p')}, {k('s')}, {k('d')},
                {k('f')}, {k('w')}, {k('z')}, {k('j')},
        };

        // ---- _endConsonantTable ----
        END_CONSONANT_TABLE = new int[][]{
                {k('t')}, {k('p')}, {k('c')}, {k('n')}, {k('m')},
                {k('g')}, {k('k')}, {k('h')},
                {k('c'), k('h')}, {k('n'), k('h')}, {k('n'), k('g')},
        };
    }

    // ------------------------------------------------------------------
    // Per-word engine state
    // ------------------------------------------------------------------

    private final int[] word = new int[MAX_BUFF];
    private int index = 0;
    private boolean tempDisableKey = false;

    // vowel scan results
    private int vowelStartIndex = 0;
    private int vowelEndIndex = 0;
    private int vowelCount = 0;
    private int vowelWillSetMark = 0;

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Converts one word of raw typed text to its Vietnamese display form.
     * Words longer than {@link #MAX_BUFF} are returned unchanged.
     */
    public static String convert(String raw) {
        String normalized = normalize(raw);
        if (normalized == null || normalized.isEmpty()) {
            return normalized;
        }
        if (normalized.length() > MAX_BUFF) {
            return raw;
        }
        TelexEngine e = new TelexEngine();
        for (int i = 0; i < normalized.length(); i++) {
            e.feed(normalized.charAt(i));
        }
        return e.render(raw);
    }

    private static final String UO_SUFFIXES = "|uong|uon|uoi|uoc|uot|uom|uop|";
    private static final String IE_SUFFIXES = "|ien|ieng|iem|iet|iep|yen|yeng|yem|yet|";

    /**
     * Mod-specific pre-pass on top of OpenKey:
     * frequent lexical/glide shortcuts that pure structure cannot express.
     * - "khong" -> "khoong"
     * - bare glide endings get an explicit modifier appended ("duong" -> "duongw",
     *   "dien" -> "dieen"), so OpenKey's pair rules produce dương/người/diên
     *   while incomplete words stay literal.
     */
    private static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.equals("khong")) {
            return sameCase(raw, "khoong");
        }
        if (lower.indexOf('w') >= 0 || lower.length() < 3 || lower.length() > MAX_BUFF - 1) {
            return raw;
        }

        // split off one trailing tone key
        String body = raw;
        String toneTail = "";
        char last = Character.toLowerCase(body.charAt(body.length() - 1));
        if ("sfrxj".indexOf(last) >= 0 && body.length() > 3) {
            toneTail = String.valueOf(body.charAt(body.length() - 1));
            body = body.substring(0, body.length() - 1);
        }
        String bodyLower = body.toLowerCase(Locale.ROOT);

        String ins = null;
        int pos = body.length();
        String suf4 = bodyLower.length() >= 4 ? bodyLower.substring(bodyLower.length() - 4) : "";
        String suf3 = bodyLower.substring(Math.max(0, bodyLower.length() - 3));
        if (UO_SUFFIXES.contains("|" + suf4 + "|")) {
            ins = "w"; // e.g. duong -> duongw (modifier goes to the end, OpenKey pair rules)
        } else if (UO_SUFFIXES.contains("|" + suf3 + "|")) {
            ins = "w"; // e.g. nguoi -> nguoiw
        } else if (IE_SUFFIXES.contains("|" + suf4 + "|")) {
            ins = "e"; // dien -> dieen
            pos = body.length() - suf4.length() + 2;
        } else if (IE_SUFFIXES.contains("|" + suf3 + "|")) {
            ins = "e";
            pos = body.length() - suf3.length() + 2;
        }

        if (ins == null) {
            return raw;
        }
        char inserted = Character.isUpperCase(body.charAt(pos > 0 ? pos - 1 : 0))
                ? Character.toUpperCase(ins.charAt(0)) : ins.charAt(0);
        return body.substring(0, pos) + inserted + body.substring(pos) + toneTail;
    }

    private static String sameCase(String source, String target) {
        String upper = source.toUpperCase(Locale.ROOT);
        if (source.equals(upper)) {
            return target.toUpperCase(Locale.ROOT);
        }
        StringBuilder sb = new StringBuilder(target);
        for (int i = 0; i < Math.min(source.length(), sb.length()); i++) {
            if (Character.isUpperCase(source.charAt(i))) {
                sb.setCharAt(i, Character.toUpperCase(sb.charAt(i)));
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Key intake (port of vKeyHandleEvent, letters only)
    // ------------------------------------------------------------------

    private void feed(char c) {
        char lower = Character.toLowerCase(c);
        boolean caps = lower != c;
        if (lower < 'a' || lower > 'z') {
            return;
        }

        boolean special = isSpecialKey(lower);
        if (!special || tempDisableKey) {
            insertKey(lower, caps);
        } else {
            handleMainKey(lower, caps);
        }

        // OpenKey runs checkGrammar after every processed key except d
        if (lower != 'd') {
            checkGrammar();
        }
    }

    private static boolean isSpecialKey(char c) {
        return c == 's' || c == 'f' || c == 'r' || c == 'x' || c == 'j'
                || c == 'a' || c == 'o' || c == 'e' || c == 'w'
                || c == 'd' || c == 'z';
    }

    private static boolean isMarkKey(char c) {
        return c == 's' || c == 'f' || c == 'r' || c == 'j' || c == 'x';
    }

    private char chr(int i) {
        return (char) (word[i] & 0xFF);
    }

    private static boolean isConsonant(char c) {
        return !(c == 'a' || c == 'e' || c == 'u' || c == 'y' || c == 'i' || c == 'o');
    }

    private void insertKey(char keyCode, boolean caps) {
        if (index >= MAX_BUFF) {
            // left shift, keep the tail
            for (int i = 0; i < MAX_BUFF - 1; i++) {
                word[i] = word[i + 1];
            }
            word[MAX_BUFF - 1] = keyCode | (caps ? CAPS : 0);
        } else {
            word[index++] = keyCode | (caps ? CAPS : 0);
        }
        checkSpelling(false);

        // allow d after consonant (OpenKey insertKey tail)
        if (keyCode == 'd' && index - 2 >= 0 && isConsonant(chr(index - 2))) {
            tempDisableKey = false;
        }
    }

    // ------------------------------------------------------------------
    // Main dispatch (port of handleMainKey)
    // ------------------------------------------------------------------

    private void handleMainKey(char data, boolean caps) {
        // Z: remove marks
        if (data == 'z') {
            boolean changed = removeMarksInVowelRange();
            if (!changed) {
                insertKey(data, caps);
            }
            return;
        }

        // D: dd -> đ
        if (data == 'd') {
            boolean changed = false;
            for (int[] entry : CONSONANT_D) {
                if (index < entry.length) {
                    continue;
                }
                boolean correct = checkCorrectVowel(entry, index, 'd');
                if (!correct && index - 2 >= 0 && chr(index - 1) == 'd' && isConsonant(chr(index - 2))) {
                    correct = true; // allow d after consonant
                }
                if (correct) {
                    changed = true;
                    insertD();
                    break;
                }
            }
            if (!changed) {
                insertKey(data, caps);
            }
            return;
        }

        // Mark keys
        if (isMarkKey(data)) {
            int markMask = switch (data) {
                case 's' -> M1;
                case 'f' -> M2;
                case 'r' -> M3;
                case 'x' -> M4;
                default -> M5; // j
            };
            int[][][] groups = {
                    VOWEL_FOR_MARK.get('a'), VOWEL_FOR_MARK.get('o'),
                    VOWEL_FOR_MARK.get('e'), VOWEL_FOR_MARK.get('i'),
                    VOWEL_FOR_MARK.get('u'), VOWEL_FOR_MARK.get('y'),
            };
            boolean changed = false;
            for (int[][] charset : groups) {
                if (charset == null) {
                    continue;
                }
                for (int[] entry : charset) {
                    if (index < entry.length) {
                        continue;
                    }
                    if (checkCorrectVowel(entry, index, data)) {
                        insertMark(markMask);
                        changed = true;
                        break;
                    }
                }
                if (changed) {
                    break;
                }
            }
            if (!changed) {
                insertKey(data, caps);
            }
            return;
        }

        // Vowel-modifier keys: a/o/e (circumflex) and w (horn)
        boolean isDouble = data == 'a' || data == 'o' || data == 'e';
        boolean isW = data == 'w';
        int[][] charset = VOWEL.get(data);
        boolean changed = false;
        for (int[] entry : charset) {
            if (index < entry.length) {
                continue;
            }
            if (checkCorrectVowel(entry, index, data)) {
                changed = true;
                if (isDouble) {
                    insertAOE(data);
                } else if (isW) {
                    insertW(data);
                }
                break;
            }
        }

        if (!changed) {
            if (isW) {
                checkForStandaloneChar(caps);
            } else {
                insertKey(data, caps);
            }
        }
    }

    /** Port of checkCorrectVowel: entry matches the word tail? */
    private boolean checkCorrectVowel(int[] entry, int kIn, char markKey) {
        // ignore "qu" case
        if (index >= 2 && chr(index - 1) == 'u' && chr(index - 2) == 'q') {
            return false;
        }
        int k = kIn - 1; // OpenKey compares starting at the LAST buffered character
        for (int j = entry.length - 1; j >= 0; j--) {
            if (entry[j] != chr(k)) {
                return false;
            }
            k--;
            if (k < 0) {
                break;
            }
        }
        // limit huyền/hỏi/ngã on endings containing c/t
        if (entry.length > 1 && (markKey == 'f' || markKey == 'x' || markKey == 'r')) {
            if (entry[1] == 'c' || entry[1] == 't') {
                return false;
            }
            if (entry.length > 2 && entry[2] == 't') {
                return false;
            }
        }
        if (k >= 0 && chr(k) == chr(k + 1)) {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Transformations
    // ------------------------------------------------------------------

    private void insertD() {
        for (int ii = index - 1; ii >= 0; ii--) {
            if (chr(ii) == 'd') {
                if ((word[ii] & TONE) != 0) {
                    word[ii] &= ~TONE; // restore
                    tempDisableKey = true;
                } else {
                    word[ii] |= TONE;
                }
                break;
            }
        }
    }

    private void insertAOE(char data) {
        findAndCalculateVowel(false);
        for (int ii = vowelStartIndex; ii <= vowelEndIndex && ii < index; ii++) {
            word[ii] &= ~TONEW;
        }
        for (int ii = index - 1; ii >= 0; ii--) {
            if (chr(ii) == data) {
                if ((word[ii] & TONE) != 0) {
                    word[ii] &= ~TONE; // restore
                    if (data != 'o') {
                        tempDisableKey = true;
                    }
                } else {
                    word[ii] |= TONE;
                    word[ii] &= ~TONEW;
                }
                break;
            }
        }
    }

    private void insertW(char data) {
        findAndCalculateVowel(false);
        for (int ii = vowelStartIndex; ii <= vowelEndIndex && ii < index; ii++) {
            word[ii] &= ~TONE;
        }

        if (vowelCount > 1) {
            boolean already = (word[vowelStartIndex] & TONEW) != 0 && (word[vowelStartIndex + 1] & TONEW) != 0
                    || (word[vowelStartIndex] & TONEW) != 0 && chr(vowelStartIndex + 1) == 'i'
                    || (word[vowelStartIndex] & TONEW) != 0 && chr(vowelStartIndex + 1) == 'a';
            if (already) {
                // restore and disable temporarily
                for (int ii = vowelStartIndex; ii < index; ii++) {
                    word[ii] &= ~TONEW;
                }
                tempDisableKey = true;
                return;
            }

            if (chr(vowelStartIndex) == 'u' && chr(vowelStartIndex + 1) == 'o') {
                if (vowelStartIndex - 2 >= 0 && chr(vowelStartIndex - 2) == 't' && chr(vowelStartIndex - 1) == 'h') {
                    word[vowelStartIndex + 1] |= TONEW;
                    if (vowelStartIndex + 2 < index && chr(vowelStartIndex + 2) == 'n') {
                        word[vowelStartIndex] |= TONEW;
                    }
                } else if (vowelStartIndex - 1 >= 0 && chr(vowelStartIndex - 1) == 'q') {
                    word[vowelStartIndex + 1] |= TONEW;
                } else {
                    word[vowelStartIndex] |= TONEW;
                    word[vowelStartIndex + 1] |= TONEW;
                }
            } else if ((chr(vowelStartIndex) == 'u' && chr(vowelStartIndex + 1) == 'a')
                    || (chr(vowelStartIndex) == 'u' && chr(vowelStartIndex + 1) == 'i')
                    || (chr(vowelStartIndex) == 'u' && chr(vowelStartIndex + 1) == 'u')
                    || (chr(vowelStartIndex) == 'o' && chr(vowelStartIndex + 1) == 'i')) {
                word[vowelStartIndex] |= TONEW;
            } else if ((chr(vowelStartIndex) == 'i' && chr(vowelStartIndex + 1) == 'o')
                    || (chr(vowelStartIndex) == 'o' && chr(vowelStartIndex + 1) == 'a')) {
                word[vowelStartIndex + 1] |= TONEW;
            }
            // otherwise: no-op (OpenKey disables temporarily)
            return;
        }

        // single vowel in range
        for (int ii = index - 1; ii >= 0; ii--) {
            if (ii < vowelStartIndex) {
                break;
            }
            char c = chr(ii);
            if (c == 'a' || c == 'u' || c == 'o') {
                if ((word[ii] & TONEW) != 0) {
                    // restore and disable temporarily
                    if ((word[ii] & STANDALONE) != 0) {
                        if (c == 'u') {
                            word[ii] = 'w' | (word[ii] & CAPS);
                        } else if (c == 'o') {
                            word[ii] = 'o' | (word[ii] & CAPS);
                        }
                    } else {
                        word[ii] &= ~TONEW;
                    }
                    tempDisableKey = true;
                } else {
                    word[ii] |= TONEW;
                    word[ii] &= ~TONE;
                }
            }
        }
    }

    /** Port of checkForStandaloneChar for the w key (target ư). */
    private void checkForStandaloneChar(boolean caps) {
        // "ww" after a standalone ư: replace with plain w
        if (index > 0 && chr(index - 1) == 'u' && (word[index - 1] & TONEW) != 0
                && (word[index - 1] & STANDALONE) != 0) {
            word[index - 1] = 'w' | (caps ? CAPS : 0);
            return;
        }

        if (index == 0) {
            pushStandaloneW(caps);
            return;
        }
        if (index == 1) {
            for (char bad : STANDALONE_W_BAD) {
                if (chr(0) == bad) {
                    insertKey('w', caps);
                    return;
                }
            }
            pushStandaloneW(caps);
            return;
        }
        if (index == 2) {
            for (char[] pair : DOUBLE_W_ALLOWED) {
                if (chr(0) == pair[0] && chr(1) == pair[1]) {
                    pushStandaloneW(caps);
                    return;
                }
            }
            insertKey('w', caps);
            return;
        }
        insertKey('w', caps);
    }

    private void pushStandaloneW(boolean caps) {
        if (index >= MAX_BUFF) {
            insertKey('w', caps);
            return;
        }
        word[index++] = 'w' | TONEW | STANDALONE | (caps ? CAPS : 0);
    }

    // ------------------------------------------------------------------
    // Marks (port of insertMark / handleOldMark / handleModernMark)
    // ------------------------------------------------------------------

    private void insertMark(int markMask) {
        findAndCalculateVowel(false);
        vowelWillSetMark = 0;

        if (vowelCount == 1) {
            vowelWillSetMark = vowelEndIndex;
        } else {
            handleOldMark();
            if ((word[vowelEndIndex] & (TONE | TONEW)) != 0) {
                vowelWillSetMark = vowelEndIndex;
            }
        }
        vowelWillSetMark = clampVsm();

        if ((word[vowelWillSetMark] & markMask) != 0) {
            // duplicate mark: remove all marks and disable temporarily
            for (int ii = vowelStartIndex; ii < index; ii++) {
                word[ii] &= ~MARK_MASK;
            }
            tempDisableKey = true;
        } else {
            word[vowelWillSetMark] &= ~MARK_MASK;
            word[vowelWillSetMark] |= markMask;
            for (int ii = vowelStartIndex; ii < index; ii++) {
                if (ii != vowelWillSetMark) {
                    word[ii] &= ~MARK_MASK;
                }
            }
        }
    }

    /** Guard the chosen index against pathological states. */
    private int clampVsm() {
        if (vowelWillSetMark < 0 || vowelWillSetMark >= index || vowelWillSetMark < vowelStartIndex) {
            return vowelEndIndex < index ? vowelEndIndex : vowelStartIndex;
        }
        return vowelWillSetMark;
    }

    private void handleOldMark() {
        if (vowelCount == 0 && chr(veIClamp()) == 'i') {
            vowelWillSetMark = vowelEndIndex;
        } else {
            vowelWillSetMark = vowelStartIndex;
        }

        // rule 2
        if (vowelCount == 3 || (vowelEndIndex + 1 < index && isConsonant(chr(vowelEndIndex + 1)) && canHasEndConsonant())) {
            vowelWillSetMark = vowelStartIndex + 1;
        }

        // rule 3: prefer a vowel that already carries ^ or horn
        for (int ii = vowelStartIndex; ii <= vowelEndIndex && ii < index; ii++) {
            if ((chr(ii) == 'e' && (word[ii] & TONE) != 0) || (chr(ii) == 'o' && (word[ii] & TONEW) != 0)) {
                vowelWillSetMark = ii;
                break;
            }
        }
    }

    private int veIClamp() {
        return vowelEndIndex < index ? vowelEndIndex : index - 1;
    }

    /** Unused modern variant kept for reference/parity with OpenKey. */
    @SuppressWarnings("unused")
    private void handleModernMark() {
        vowelWillSetMark = vowelEndIndex;
        // (modern orthography rules omitted - OpenKey defaults to the old style)
    }

    private boolean removeMarksInVowelRange() {
        findAndCalculateVowel(true);
        boolean changed = false;
        for (int ii = vowelStartIndex; ii <= vowelEndIndex && ii < index; ii++) {
            if ((word[ii] & MARK_MASK) != 0) {
                word[ii] &= ~MARK_MASK;
                changed = true;
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // Vowel discovery (port of findAndCalculateVowel)
    // ------------------------------------------------------------------

    private void findAndCalculateVowel(boolean forGrammar) {
        vowelCount = 0;
        vowelStartIndex = 0;
        vowelEndIndex = 0;
        for (int iii = index - 1; iii >= 0; iii--) {
            char c = chr(iii);
            if (isConsonant(c)) {
                if (vowelCount > 0) {
                    break;
                }
            } else {
                if (vowelCount == 0) {
                    vowelEndIndex = iii;
                }
                if (!forGrammar) {
                    if (iii - 1 >= 0 && ((c == 'i' && chr(iii - 1) == 'g') || (c == 'u' && chr(iii - 1) == 'q'))) {
                        break;
                    }
                }
                vowelStartIndex = iii;
                vowelCount++;
            }
        }
        // don't count the u in "qu"
        if (vowelStartIndex - 1 >= 0 && chr(vowelStartIndex) == 'u' && chr(vowelStartIndex - 1) == 'q') {
            vowelStartIndex++;
            vowelCount--;
        }
    }

    private boolean canHasEndConsonant() {
        int[][] vo = VOWEL_COMBINE.get(chr(vowelStartIndex));
        if (vo == null) {
            return false;
        }
        for (int[] pattern : vo) {
            int kk = vowelStartIndex;
            int iii = 1;
            for (; iii < pattern.length; iii++) {
                if (kk > vowelEndIndex || kk >= index
                        || (chr(kk) | (word[kk] & TONE) | (word[kk] & TONEW)) != pattern[iii]) {
                    break;
                }
                kk++;
            }
            if (iii >= pattern.length) {
                return pattern[0] == 1;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Grammar fix-up (port of checkGrammar)
    // ------------------------------------------------------------------

    private void checkGrammar() {
        if (index <= 1 || index >= MAX_BUFF) {
            return;
        }
        findAndCalculateVowel(true);
        if (vowelCount == 0) {
            return;
        }

        boolean checked = false;
        int l = vowelStartIndex;

        // fix u/o horn split before ending consonants: "thuơn" -> thương, ưoi, ưom, ưoc
        if (index >= 3) {
            for (int i = index - 1; i >= 0; i--) {
                char c = chr(i);
                if (c == 'n' || c == 'c' || c == 'i' || c == 'm' || c == 'p' || c == 't') {
                    if (i - 2 >= 0 && chr(i - 1) == 'o' && chr(i - 2) == 'u') {
                        boolean xor = ((word[i - 1] & TONEW) != 0) ^ ((word[i - 2] & TONEW) != 0);
                        if (xor) {
                            word[i - 2] |= TONEW;
                            word[i - 1] |= TONEW;
                            checked = true;
                            break;
                        }
                    }
                }
            }
        }

        // re-place an existing mark onto the correct vowel
        if (index >= 2) {
            for (int i = l; i <= vowelEndIndex && i < index; i++) {
                int mark = word[i] & MARK_MASK;
                if (mark != 0) {
                    word[i] &= ~MARK_MASK;
                    insertMark(mark);
                    if (i != vowelWillSetMark) {
                        checked = true;
                    }
                    break;
                }
            }
        }
        // (output rebuild unnecessary: the whole word is re-rendered by the caller)
    }

    // ------------------------------------------------------------------
    // Spelling gate (port of checkSpelling)
    // ------------------------------------------------------------------

    private boolean spellOK;
    private boolean spellVowelOK;

    private void checkSpelling(boolean forceCheckVowel) {
        spellOK = false;
        spellVowelOK = true;
        int end = index;

        if (end > 0) {
            int j = 0;
            if (isConsonant(chr(0))) {
                outer:
                for (int[] onset : CONSONANT_TABLE) {
                    if (end < onset.length) {
                        continue;
                    }
                    for (int jj = 0; jj < onset.length; jj++) {
                        if (end > jj && onset[jj] != chr(jj)) {
                            continue outer;
                        }
                    }
                    j = onset.length;
                    break;
                }
            }

            if (j == end) {
                spellOK = true;
            } else {
                int k = j;
                vowelStartIndex = k;
                if (chr(vowelStartIndex) == 'u' && k > 0 && k < end - 1 && chr(vowelStartIndex - 1) == 'q') {
                    k = k + 1;
                    j = k;
                    vowelStartIndex = k;
                } else if (index >= 2 && chr(0) == 'g' && chr(1) == 'i' && index > 2 && isConsonant(chr(2))) {
                    vowelStartIndex = k = j = 1;
                }
                for (int l = 0; l < 3; l++) {
                    if (k < end && !isConsonant(chr(k))) {
                        k++;
                    }
                }
                if (k > j) {
                    spellVowelOK = false;
                    if (k - j > 1 && forceCheckVowel) {
                        int[][] vowelSet = VOWEL_COMBINE.get(chr(j));
                        if (vowelSet != null) {
                            for (int[] pattern : vowelSet) {
                                boolean bad = false;
                                int ii = 1;
                                for (; ii < pattern.length; ii++) {
                                    if (j + ii - 1 < end
                                            && pattern[ii] != (chr(j + ii - 1) | (word[j + ii - 1] & TONEW) | (word[j + ii - 1] & TONE))) {
                                        bad = true;
                                        break;
                                    }
                                }
                                if (bad || (k < end && pattern[0] == 0)
                                        || (j + ii - 1 < end && !isConsonant(chr(j + ii - 1)))) {
                                    continue;
                                }
                                spellVowelOK = true;
                                break;
                            }
                        }
                    } else if (!isConsonant(chr(j))) {
                        spellVowelOK = true;
                    }

                    // ending consonants
                    endLoop:
                    for (int[] ec : END_CONSONANT_TABLE) {
                        for (int jj = 0; jj < ec.length; jj++) {
                            if (end > k + jj && ec[jj] != chr(k + jj)) {
                                continue endLoop;
                            }
                        }
                        if (k + ec.length >= end) {
                            spellOK = true;
                            break;
                        }
                    }

                    // "ch"/"t" endings reject huyền/ngã (and require sắc/nặng/ngang)
                    if (spellOK) {
                        if (index >= 3 && chr(index - 1) == 'h' && chr(index - 2) == 'c') {
                            int v = word[index - 3];
                            if ((v & M2) != 0 || (v & M3) != 0 || (v & M4) != 0) {
                                spellOK = false;
                            }
                        } else if (index >= 2 && chr(index - 1) == 't') {
                            int v = word[index - 2];
                            if ((v & M2) != 0 || (v & M3) != 0 || (v & M4) != 0) {
                                spellOK = false;
                            }
                        }
                    }
                }
            }
        } else {
            spellOK = true;
        }
        tempDisableKey = !(spellOK && spellVowelOK);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private String render(String original) {
        StringBuilder sb = new StringBuilder(index);
        for (int i = 0; i < index; i++) {
            int v = word[i];
            char c = (char) (v & 0xFF);
            boolean caps = (v & CAPS) != 0;

            if (c == 'd' && (v & TONE) != 0) {
                c = 'đ';
            } else if ((v & TONE) != 0) {
                c = switch (c) {
                    case 'a' -> 'â';
                    case 'e' -> 'ê';
                    case 'o' -> 'ô';
                    default -> c;
                };
            } else if ((v & TONEW) != 0) {
                c = switch (c) {
                    case 'a' -> 'ă';
                    case 'o' -> 'ơ';
                    case 'u' -> 'ư';
                    case 'w' -> 'ư'; // standalone w
                    default -> c;
                };
            }

            int mark = v & MARK_MASK;
            if (mark != 0) {
                String[] marks = MARKS.get(c);
                if (marks != null) {
                    int ti = switch (mark) {
                        case M1 -> 1;
                        case M2 -> 2;
                        case M3 -> 3;
                        case M4 -> 4;
                        case M5 -> 5;
                        default -> 0;
                    };
                    c = marks[ti].charAt(0);
                }
            }

            sb.append(caps ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }
}
