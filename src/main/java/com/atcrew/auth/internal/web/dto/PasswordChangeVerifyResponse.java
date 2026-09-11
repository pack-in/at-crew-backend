package com.atcrew.auth.internal.web.dto;

// reauthToken은 2단계(새 비밀번호 입력) 확정 요청에만 쓰는 짧은 수명의 토큰이다.
public record PasswordChangeVerifyResponse(String reauthToken) {}
