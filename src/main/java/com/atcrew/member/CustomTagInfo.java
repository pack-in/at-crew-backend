package com.atcrew.member;

/** 직접입력 태그 한 건 — 어떤 항목의 값인지와 값 자체. */
public record CustomTagInfo(
        CustomTagType type,
        String value
) {
}
