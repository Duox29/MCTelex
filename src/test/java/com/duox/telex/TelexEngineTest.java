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
        t("os", "ó");
        t("toans", "toán");
        t("mootj", "một");
        t("motj", "mọt"); // plain o + nặng: engine respects the letters typed
        t("mootj", "một");
        t("bonr", "bỏn");
        t("boonr", "bổn"); // bốn with hỏi
        t("khong", "không");   // lexical override
        t("khoongs", "khống"); // explicit oo + sắc
        t("khoong", "không");
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
        t("muwa", "mưa");
    }

    // ---- full words ----
    @Test void words() {
        t("chao", "chao");
        t("chaos", "cháo");
        t("duong", "dương");   // implicit trailing w
        t("duongs", "dướng");
        t("truong", "trương");
        t("viet", "viêt");
        t("viets", "viết");
        t("vietj", "việt");
        t("nam", "nam");
        t("nguoi", "ngươi");   // implicit trailing w
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

    // ---- flexible modifier order (the reason for the OpenKey port) ----
    @Test void flexibleOrder() {
        t("nguowif", "người");
        t("nguoiwf", "người");
        t("nguowfi", "người");
        t("luowif", "lười");
        t("chuiwr", "chửi");
        t("chuowri", "chưởi"); // letters-faithful: hỏi lands on ơ
        t("choiw", "chơi");
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
        t("DUONG", "DƯƠNG");
        t("KHONG", "KHÔNG");
        t("khong", "không");
        t("ANH", "ANH");
    }

    // ---- partial typing renders progressively ----
    @Test void partial() {
        t("d", "d");
        t("dd", "đ");
        t("gi", "gi");
        t("gif", "gì");
        t("t", "t");
        t("s", "s");   // tone key with nothing to tone stays literal
        t("duo", "duo"); // incomplete word stays literal
        t("nguo", "nguo");
        t("kho", "kho");
        t("khoo", "khô"); // unambiguous digraph may render early
    }
}
