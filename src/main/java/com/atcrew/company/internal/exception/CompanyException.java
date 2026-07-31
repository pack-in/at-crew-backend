package com.atcrew.company.internal.exception;

import com.atcrew.common.exception.DomainException;

public class CompanyException extends DomainException {

    public CompanyException(CompanyErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public CompanyException(CompanyErrorCode errorCode, String logDetail) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), logDetail);
    }
}
