package com.atcrew.member.exception;

import com.atcrew.common.DomainException;

public class MemberException extends DomainException {

    public MemberException(MemberErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public MemberException(MemberErrorCode errorCode, String logDetail) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), logDetail);
    }
}
