package com.atcrew.auth;

public interface AuthService {

    AuthInfo loginWithEmail(EmailLoginCommand command);

    AuthInfo loginWithGoogle(String firebaseIdToken);

    AuthInfo registerWithEmail(EmailRegisterCommand command);

    AuthInfo registerWithGoogle(GoogleRegisterCommand command);

    AuthInfo refresh(String refreshToken);
}
