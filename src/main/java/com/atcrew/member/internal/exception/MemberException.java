package com.atcrew.member.internal.exception;

import com.atcrew.common.exception.DomainException;

public class MemberException extends DomainException {

    public MemberException(MemberErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public MemberException(MemberErrorCode errorCode, String logDetail) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), logDetail);
    }
}
