package com.atcrew.auth.exception;

import com.atcrew.common.DomainException;

public class AuthException extends DomainException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public AuthException(AuthErrorCode errorCode, Throwable cause) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), cause);
    }
}
