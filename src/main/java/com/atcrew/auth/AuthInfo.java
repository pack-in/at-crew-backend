package com.atcrew.auth;

import com.atcrew.member.MemberInfo;

public record AuthInfo(
        String accessToken,
        String refreshToken,
        MemberInfo member,
        boolean isNewUser
) {}
