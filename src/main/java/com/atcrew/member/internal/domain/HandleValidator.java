package com.atcrew.member.internal.domain;

import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 사용자 아이디(handle) 형식 검증기 (member-handle-design §1.2).
 * strip() 후 정해진 순서로 검사하고 첫 실패 하나만 돌려준다. 가입·변경·사전 확인이 공유한다.
 */
public final class HandleValidator {

    public static final int MIN_LENGTH = 4;
    /** 이 길이 이상이면 너무 긴 값 (허용 최대 14자) */
    public static final int MAX_LENGTH_EXCLUSIVE = 15;

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_]+");

    // FE 최상위 라우트 추가 시 갱신. 4자 미만·하이픈 포함 이름은 형식 검사에서 이미 걸리므로 넣지 않는다.
    private static final Set<String> RESERVED = Set.of(
            "artwork", "bookmark", "community", "login", "portfolio", "pricing", "privacy",
            "profile", "search", "setting", "terms", "trash", "upload", "billing", "_next",
            "admin", "help", "about", "signup", "settings", "company");

    private HandleValidator() {
    }

    /** 예외 없이 첫 실패 코드만 돌려준다. 통과하면 empty. */
    public static Optional<MemberErrorCode> check(String raw) {
        if (raw == null) {
            return Optional.of(MemberErrorCode.HANDLE_REQUIRED);
        }
        return checkStripped(raw.strip());
    }

    /** 통과 시 strip된 값을 돌려주고, 실패 시 {@link MemberException}을 던진다. */
    public static String validate(String raw) {
        String stripped = raw == null ? "" : raw.strip();
        checkStripped(stripped).ifPresent(code -> {
            throw new MemberException(code);
        });
        return stripped;
    }

    private static Optional<MemberErrorCode> checkStripped(String handle) {
        if (handle.isEmpty()) {
            return Optional.of(MemberErrorCode.HANDLE_REQUIRED);
        }
        if (handle.length() < MIN_LENGTH) {
            return Optional.of(MemberErrorCode.HANDLE_TOO_SHORT);
        }
        if (handle.length() >= MAX_LENGTH_EXCLUSIVE) {
            return Optional.of(MemberErrorCode.HANDLE_TOO_LONG);
        }
        if (!ALLOWED.matcher(handle).matches()) {
            return Optional.of(MemberErrorCode.HANDLE_INVALID_CHARACTERS);
        }
        if (handle.contains("__")) {
            return Optional.of(MemberErrorCode.HANDLE_CONSECUTIVE_UNDERSCORES);
        }
        if (RESERVED.contains(handle.toLowerCase(Locale.ROOT))) {
            return Optional.of(MemberErrorCode.HANDLE_RESERVED);
        }
        return Optional.empty();
    }
}
