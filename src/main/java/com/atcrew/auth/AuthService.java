package com.atcrew.auth;

public interface AuthService {

    AuthInfo loginWithEmail(EmailLoginCommand command);

    AuthInfo loginWithGoogle(String firebaseIdToken);

    AuthInfo registerWithEmail(EmailRegisterCommand command);

    AuthInfo registerWithGoogle(GoogleRegisterCommand command);

    AuthInfo refresh(String refreshToken);

    /**
     * 로그아웃 — 전달받은 Refresh Token을 폐기해 재발급 경로를 끊는다.
     * Access Token은 서버 상태가 없는 JWT라 만료까지 유효하며, 별도 무효화 대상이 아니다.
     * 이미 폐기됐거나 만료된 토큰이면 아무것도 하지 않는다(멱등).
     */
    void logout(String memberId, String refreshToken);

    /**
     * 비밀번호 변경 — EMAIL 가입 계정 전용. 현재 비밀번호를 확인한 뒤 새 비밀번호로 교체하고,
     * currentRefreshToken(현재 기기 세션)을 제외한 해당 회원의 나머지 Refresh Token을 모두 폐기한다
     * (설정-R13 — 현재 기기는 유지, 다른 기기만 로그아웃).
     */
    void changePassword(String memberId, String currentPassword, String newPassword, String currentRefreshToken);

    /**
     * 비밀번호 재설정 요청(§7) — 가입 여부·계정 상태와 무관하게 항상 성공적으로 끝난다(예외를 던지지
     * 않음, enumeration 방지). EMAIL 활성 회원이면 재설정 링크 메일을, 동일 이메일의 GOOGLE 계정만
     * 있으면 안내 메일을 실제로 발송한다 — 응답으로는 어느 쪽인지 구분할 수 없다.
     */
    void requestPasswordReset(String email);

    /**
     * 비밀번호 재설정 확정(§7) — 토큰은 단발성이며 성공 시 즉시 소비되고, 해당 회원의 Refresh Token도
     * 모두 폐기된다(전 기기 로그아웃).
     */
    void confirmPasswordReset(String token, String newPassword);
}
