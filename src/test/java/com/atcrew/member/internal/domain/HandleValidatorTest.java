package com.atcrew.member.internal.domain;

import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandleValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "　　"})
    void 빈_값은_REQUIRED(String raw) {
        assertThat(HandleValidator.check(raw)).contains(MemberErrorCode.HANDLE_REQUIRED);
    }

    @Test
    void null_validate는_REQUIRED_예외() {
        assertThatThrownBy(() -> HandleValidator.validate(null))
                .isInstanceOfSatisfying(MemberException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HANDLE_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a", "a_b"})
    void 세_자_이하는_TOO_SHORT(String raw) {
        assertThat(HandleValidator.check(raw)).contains(MemberErrorCode.HANDLE_TOO_SHORT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd", "abcdefghijklmn", "a1_b2_c3_d4_e5"})
    void 네_자_이상_열네_자_이하는_통과(String raw) {
        assertThat(raw.length()).isBetween(4, 14);
        assertThat(HandleValidator.check(raw)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefghijklmno", "abcdefghijklmnopqrstuvwxyz", "abcdefghijklmn!!!!"})
    void 열다섯_자_이상은_TOO_LONG(String raw) {
        assertThat(HandleValidator.check(raw)).contains(MemberErrorCode.HANDLE_TOO_LONG);
    }

    @Test
    void 단일_밑줄은_첫_글자와_끝_글자에도_허용() {
        assertThat(HandleValidator.check("_abc")).isEmpty();
        assertThat(HandleValidator.check("abc_")).isEmpty();
        assertThat(HandleValidator.check("_ab_")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab__cd", "__ab", "ab__", "a___b"})
    void 연속_밑줄은_CONSECUTIVE_UNDERSCORES(String raw) {
        assertThat(HandleValidator.check(raw)).contains(MemberErrorCode.HANDLE_CONSECUTIVE_UNDERSCORES);
    }

    @Test
    void 앞뒤_공백과_전각_공백은_제거된다() {
        assertThat(HandleValidator.validate("  pack_1n  ")).isEqualTo("pack_1n");
        assertThat(HandleValidator.validate("　pack_1n　")).isEqualTo("pack_1n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab cd", "ab　cd", "ab-cd", "ab.cd", "가나다라마", "abcd한글", "ab@cd"})
    void 허용_문자_외는_INVALID_CHARACTERS(String raw) {
        assertThat(HandleValidator.check(raw)).contains(MemberErrorCode.HANDLE_INVALID_CHARACTERS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "ARTWORK", "Artwork", "_next", "_NEXT", "Settings", "signup"})
    void 예약어는_대소문자_무관하게_RESERVED(String raw) {
        assertThat(HandleValidator.check(raw)).contains(MemberErrorCode.HANDLE_RESERVED);
    }

    @ParameterizedTest
    @CsvSource({
            "'a b',             HANDLE_TOO_SHORT",
            "'가나다라마',        HANDLE_INVALID_CHARACTERS",
            "'abcdefghijklmn!!', HANDLE_TOO_LONG",
            "'__ab',            HANDLE_CONSECUTIVE_UNDERSCORES",
            "'admin',           HANDLE_RESERVED",
            "'a b c d',         HANDLE_INVALID_CHARACTERS"
    })
    void 다중_위반은_우선순위_첫_하나만(String raw, MemberErrorCode expected) {
        assertThat(HandleValidator.check(raw)).contains(expected);
    }

    @Test
    void validate는_실패_시_해당_코드의_MemberException() {
        assertThatThrownBy(() -> HandleValidator.validate("ab"))
                .isInstanceOfSatisfying(MemberException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HANDLE_TOO_SHORT"));
    }
}
