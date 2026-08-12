package com.atcrew.auth;

import com.atcrew.member.MemberInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증 결과 — 토큰 쌍과 로그인한 회원 정보")
public record AuthInfo(
        @Schema(description = "Access Token (JWT). Authorization: Bearer {accessToken} 헤더로 사용, 유효기간 1시간",
                example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIwMTlmZjM4MS04MTU2LTc3MjAtOGFmNy0zZmMzNDU3NGJjYzYiLCJ0eXBlIjoiYWNjZXNzIn0.signature")
        String accessToken,

        @Schema(description = "Refresh Token (JWT). POST /api/auth/refresh에 1회만 사용할 수 있고 갱신 시 새 값으로 교체된다",
                example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIwMTlmZjM4MS04MTU2LTc3MjAtOGFmNy0zZmMzNDU3NGJjYzYiLCJ0eXBlIjoicmVmcmVzaCJ9.signature")
        String refreshToken,

        @Schema(description = "로그인·가입한 회원 정보")
        MemberInfo member,

        @Schema(description = "이번 요청으로 새로 가입했는지 여부. 회원가입은 true, 로그인·토큰 갱신은 false",
                example = "true")
        boolean isNewUser
) {}
