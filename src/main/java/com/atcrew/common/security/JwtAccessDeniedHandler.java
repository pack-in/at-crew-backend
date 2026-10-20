package com.atcrew.common.security;

import com.atcrew.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증은 됐지만 권한이 없는 요청에 403 JSON을 바로 쓴다.
 *
 * <p>기본 처리기는 {@code sendError(403)}를 불러 서블릿 컨테이너가 {@code /error}로 다시 디스패치한다.
 * 그 디스패치에는 {@link JwtAuthenticationFilter}(OncePerRequestFilter)가 돌지 않아 인증 정보가 없고,
 * {@code /error}도 인증 대상이라 결국 401 UNAUTHENTICATED가 나간다 — 로그인한 회원이 "로그인이 필요하다"는
 * 응답을 받는다. MockMvc는 이 디스패치를 하지 않아 테스트로는 드러나지 않았다(PR #270 코드 리뷰).
 * {@link JwtAuthenticationEntryPoint}처럼 상태와 본문을 직접 써서 디스패치를 만들지 않는다.
 */
@Component
class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final String BODY = "{\"code\":\"" + CommonErrorCode.FORBIDDEN.name()
            + "\",\"message\":\"" + CommonErrorCode.FORBIDDEN.getMessage() + "\"}";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
