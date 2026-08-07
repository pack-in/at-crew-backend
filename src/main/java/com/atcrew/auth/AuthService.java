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
     * 해당 회원의 Refresh Token을 모두 폐기한다.
     */
    void changePassword(String memberId, String currentPassword, String newPassword);
}
