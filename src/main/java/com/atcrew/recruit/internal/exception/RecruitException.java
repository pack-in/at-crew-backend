package com.atcrew.recruit.internal.exception;

import com.atcrew.common.exception.DomainException;

public class RecruitException extends DomainException {

    public RecruitException(RecruitErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public RecruitException(RecruitErrorCode errorCode, String logDetail) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), logDetail);
    }
}
