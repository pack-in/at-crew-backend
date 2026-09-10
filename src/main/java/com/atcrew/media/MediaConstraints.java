package com.atcrew.media;

/**
 * media를 소비하는 모듈이 업로드 입력을 검증할 때 공유하는 제약값.
 *
 * <p>Presigned PUT은 서명에 Content-Length 조건이 없어 R2가 크기를 강제하지 못한다. 그래서 상한 검사는
 * 두 겹이다 — presign 발급 시 클라이언트가 신고한 크기로 미리 거르고(여기), 변환 직전 Worker가 실제 R2
 * 객체 크기로 다시 본다(cloudflare-worker/src/index.js의 MAX_ORIGINAL_BYTES). 두 값은 반드시 같아야 한다.
 */
public final class MediaConstraints {

    /** 업로드 원본 용량 상한 10MB. */
    public static final long MAX_ORIGINAL_BYTES = 10L * 1024 * 1024;

    private MediaConstraints() {
    }
}
