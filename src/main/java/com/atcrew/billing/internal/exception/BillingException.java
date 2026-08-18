package com.atcrew.billing.internal.exception;

import com.atcrew.common.exception.DomainException;

public class BillingException extends DomainException {

    public BillingException(BillingErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public BillingException(BillingErrorCode errorCode, String logDetail) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), logDetail);
    }

    public BillingException(BillingErrorCode errorCode, Throwable cause) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), cause);
    }
}
