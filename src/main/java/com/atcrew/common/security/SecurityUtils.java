package com.atcrew.common;

import com.atcrew.common.security.MemberPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public String getCurrentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof MemberPrincipal principal) {
            return principal.memberId();
        }
        throw new IllegalStateException("인증된 사용자 정보를 찾을 수 없습니다");
    }
}
