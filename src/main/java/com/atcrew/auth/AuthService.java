package com.atcrew.auth;

public interface AuthService {

    AuthInfo login(String firebaseIdToken);

    AuthInfo refresh(String refreshToken);
}
