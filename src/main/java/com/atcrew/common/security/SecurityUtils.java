package com.atcrew.common.security;

import com.atcrew.common.CommonErrorCode;
import com.atcrew.common.DomainException;
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
        throw new DomainException(
                CommonErrorCode.UNAUTHENTICATED.getStatus(),
                CommonErrorCode.UNAUTHENTICATED.name(),
                CommonErrorCode.UNAUTHENTICATED.getMessage());
    }
}
