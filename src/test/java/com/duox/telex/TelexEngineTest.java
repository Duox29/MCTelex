package com.duox.telex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TelexEngineTest {

    private void t(String raw, String expected) {
        assertEquals(expected, TelexEngine.convert(raw), "raw=\"" + raw + "\"");
    }

    // ---- tone marks ----
    @Test void tones() {
        t("as", "á");
        t("af", "à");
        t("ar", "ả");
        t("ax", "ã");
        t("aj", "ạ");
        t("es", "é");
        t("ys", "ý");
        t("os", "ó");       // "ó" is a valid exclamation, like real telex
        t("toans", "toán");
        t("khong", "không");
        t("khoongs", "khống"); // explicit oo + sac
    }

    // ---- vowel keys ----
    @Test void vowels() {
        t("aa", "â");
        t("aw", "ă");
        t("ee", "ê");
        t("oo", "ô");
        t("ow", "ơ");
        t("uw", "ư");
        t("w", "ư");
        t("wa", "ưa");
        t("dd", "đ");
        t("ddas", "đá");
    }

    // ---- full words ----
    @Test void words() {
        t("chao", "chao");
        t("chaos", "cháo");
        t("duong", "dương");
        t("duwong", "dương");
        t("duongs", "dướng");
        t("duongr", "dưởng"); // hỏi: như trong "nuôi dưỡng"
        t("duongj", "dượng"); // nặng
        t("viet", "viêt");
        t("viets", "viết");
        t("vietj", "việt");
        t("nam", "nam");
        t("truong", "trương");
        t("nguoi", "ngươi");
        t("nguois", "ngưới");
        t("nguooi", "nguôi");
        t("tuoi", "tươi");
        t("cuocs", "cước");
        t("diem", "diêm");
        t("dien", "diên");
        t("yen", "yên");
        t("toan", "toan");
        t("toans", "toán");
    }

    // ---- english passes through ----
    @Test void english() {
        t("hello", "hello");
        t("english", "english");
        t("thanks", "thanks");
        t("ok", "ok");
        t("b", "b");
        t("xyzzy", "xyzzy");
    }

    // ---- case handling ----
    @Test void casing() {
        t("Chaos", "Cháo");
        t("DD", "Đ");
        t("KHONG", "KHÔNG");
        t("khong", "không");
        t("ANH", "ANH");
    }

    // ---- partial typing renders progressively ----
    @Test void partial() {
        t("d", "d");
        t("gi", "gi");
        t("gif", "gì");
        t("t", "t");
        t("s", "s");   // tone key with nothing to tone stays literal
        t("duo", "dươ");
    }
}
