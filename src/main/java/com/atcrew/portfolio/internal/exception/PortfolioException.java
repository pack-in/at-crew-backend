package com.atcrew.portfolio.internal.exception;

import com.atcrew.common.exception.DomainException;

public class PortfolioException extends DomainException {

    public PortfolioException(PortfolioErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public PortfolioException(PortfolioErrorCode errorCode, String logDetail) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), logDetail);
    }
}
