package com.atcrew.auth.internal.exception;

import com.atcrew.common.exception.DomainException;

public class AuthException extends DomainException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public AuthException(AuthErrorCode errorCode, Throwable cause) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), cause);
    }
}
