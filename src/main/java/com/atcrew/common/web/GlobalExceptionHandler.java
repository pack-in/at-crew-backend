package com.atcrew.common.web;

import com.atcrew.common.exception.DomainException;
import com.atcrew.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException e) {
        if (e.getStatus().is5xxServerError()) {
            log.error("서버 오류: {}", e.getCode(), e);
        } else if (e.getStatus() == HttpStatus.UNAUTHORIZED || e.getStatus() == HttpStatus.FORBIDDEN) {
            // 인증·인가 실패 — prod에서 보안 이벤트 추적 필수. 스택트레이스는 남기지 않는다:
            // 이 예외의 발생 지점은 코드에 뻔히 드러나 있는 반면, 크리덴셜 스터핑처럼 반복 실패가
            // 쏟아지면 한 건당 약 10KB의 스택트레이스가 Loki 한도를 빠르게 소모한다
            // (docs/design/observability-design.md §4).
            log.warn("인증/인가 실패: {} {}", e.getCode(), e.getMessage());
        } else {
            // 400·404·409 등 클라이언트 오류 — 노이즈 방지를 위해 DEBUG
            log.debug("클라이언트 오류: {} {}", e.getCode(), e.getMessage());
        }
        if (e.getLogDetail() != null) {
            // logDetail 레벨은 메인 로그 레벨과 동일하게 유지
            if (e.getStatus().is5xxServerError()) {
                log.error("[{}] 상세: {}", e.getCode(), e.getLogDetail());
            } else if (e.getStatus() == HttpStatus.UNAUTHORIZED || e.getStatus() == HttpStatus.FORBIDDEN) {
                log.warn("[{}] 상세: {}", e.getCode(), e.getLogDetail());
            } else {
                log.debug("[{}] 상세: {}", e.getCode(), e.getLogDetail());
            }
        }
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    // Bean Validation (@Valid) 실패
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("입력값이 올바르지 않습니다");
        return ResponseEntity.badRequest().body(ApiResponse.error("COMMON_INVALID_INPUT", message));
    }

    // JSON 파싱 실패 (잘못된 포맷, 존재하지 않는 enum 값, 날짜 형식 오류 등)
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("COMMON_INVALID_INPUT", "요청 본문의 형식이 올바르지 않습니다"));
    }

    // 그 외 Spring MVC 인프라 예외 (405 메서드 불일치, 415 미디어 타입, 404 없는 URL 등)
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = switch (status.value()) {
            case 404 -> "요청한 리소스를 찾을 수 없습니다";
            case 405 -> "허용되지 않는 HTTP 메서드입니다";
            case 415 -> "지원하지 않는 미디어 타입입니다";
            default -> "요청을 처리할 수 없습니다";
        };
        return new ResponseEntity<>(ApiResponse.error("HTTP_" + status.value(), message), headers, status);
    }

    // @Validated + PathVariable/RequestParam 제약 위반
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse("입력값이 올바르지 않습니다");
        return ResponseEntity.badRequest().body(ApiResponse.error("COMMON_INVALID_INPUT", message));
    }

    // 예상치 못한 서버 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("COMMON_INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다"));
    }
}
