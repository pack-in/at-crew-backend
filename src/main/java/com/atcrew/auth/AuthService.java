package com.atcrew.auth;

public interface AuthService {

    AuthInfo login(String firebaseIdToken);

    AuthInfo register(RegisterCommand command);

    AuthInfo refresh(String refreshToken);
}
