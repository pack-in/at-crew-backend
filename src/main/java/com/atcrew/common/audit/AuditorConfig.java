package com.atcrew.common.audit;

import com.atcrew.common.security.MemberPrincipal;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 데이터 변경 주체("누가")를 행에 남기기 위한 감사자 공급자(이슈 #138).
 *
 * <p>지금까지 감사 정보는 {@code @CreatedDate}/{@code @LastModifiedDate}로 "언제"만 남았다.
 * 주체는 {@code JwtAuthenticationFilter}가 MDC에 넣어 로그에만 찍혔기 때문에 로그 보존 기간이
 * 지나면 사라졌다. 이 빈이 채우는 {@code @LastModifiedBy}는 DB에 영구히 남는다.
 *
 * <p>{@link com.atcrew.common.security.SecurityUtils#getCurrentMemberId()}와 달리 주체가 없을 때 예외를 던지지 않는다 —
 * 스케줄러·비동기 이벤트·결제 웹훅처럼 인증 주체가 원래 없는 경로에서도 엔티티는 저장되어야 하고,
 * 그런 변경은 {@link #SYSTEM}으로 구분해 남기는 것이 목적에 맞다.
 */
@Configuration
public class AuditorConfig {

    /** 인증 주체 없이 일어난 변경(스케줄러·비동기 리스너·외부 웹훅 처리). */
    public static final String SYSTEM = "SYSTEM";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof MemberPrincipal principal) {
                return Optional.of(principal.memberId());
            }
            return Optional.of(SYSTEM);
        };
    }
}
