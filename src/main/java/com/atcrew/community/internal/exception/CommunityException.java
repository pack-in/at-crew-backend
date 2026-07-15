package com.atcrew.community.internal.exception;

import com.atcrew.common.exception.DomainException;

public class CommunityException extends DomainException {

    public CommunityException(CommunityErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public CommunityException(CommunityErrorCode errorCode, String logDetail) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), logDetail);
    }
}
