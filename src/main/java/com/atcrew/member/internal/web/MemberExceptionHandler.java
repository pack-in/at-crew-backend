package com.atcrew.member.internal.web;

import com.atcrew.common.ApiResponse;
import com.atcrew.member.exception.MemberErrorCode;
import com.atcrew.member.exception.MemberException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class MemberExceptionHandler {

    @ExceptionHandler(MemberException.class)
    public ResponseEntity<ApiResponse<Void>> handle(MemberException e) {
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode().name(), e.getMessage()));
    }

    // existsBy 체크 후 save 사이 동시 요청으로 인한 unique index 충돌 → 500 방지
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException e) {
        MemberErrorCode code = MemberErrorCode.DUPLICATE_MEMBER_INFO;
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }
}
