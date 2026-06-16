package com.atcrew.artwork.internal.exception;

import com.atcrew.common.exception.DomainException;

public class ArtworkException extends DomainException {

    public ArtworkException(ArtworkErrorCode errorCode) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    public ArtworkException(ArtworkErrorCode errorCode, String logDetail) {
        super(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), logDetail);
    }
}
