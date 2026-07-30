package com.atcrew.search.internal.exception;

import com.atcrew.common.exception.DomainException;

public class SearchException extends DomainException {

    public SearchException(SearchErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public SearchException(SearchErrorCode errorCode, String logDetail) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), logDetail);
    }

    public SearchException(SearchErrorCode errorCode, Throwable cause) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), cause);
    }
}
