package com.atcrew.media;

/**
 * media를 소비하는 모듈이 업로드 입력을 검증할 때 공유하는 제약값.
 *
 * <p>Presigned PUT은 서명에 Content-Length 조건이 없어 R2가 크기를 강제하지 못한다. 그래서 상한 검사는
 * 두 겹이다 — presign 발급 시 클라이언트가 신고한 크기로 미리 거르고(여기), 변환 직전 Worker가 실제 R2
 * 객체 크기로 다시 본다(cloudflare-worker/src/index.js의 MAX_ORIGINAL_BYTES). 두 값은 반드시 같아야 한다.
 */
public final class MediaConstraints {

    /**
     * 업로드 원본 용량 상한 100MB — Cloudflare Images 원격 변환의 입력 한계이지 우리가 고른 정책값이 아니다.
     *
     * <p>기획(업로드-R04)은 플랜 무관 "용량 제한 없음"이므로 이 값은 기술 제약을 그대로 옮긴 것이다.
     * 넘는 파일은 클라이언트가 업로드 전에 줄여야 하고, 서버가 대신 해 줄 수 있는 일은 없다.
     *
     * <p>예전에는 20MB였다(Images 바인딩 경로의 한계). 라이트 실데이터 14,759건 중 20MB 초과가
     * 426건(2.89%)이라 Worker를 fetch(cf.image) 경로로 옮기며 100MB로 올렸다. 100MB 초과는 라이트
     * 전체에서 2건(0.01%)이고 Images 자체의 한계라 어떤 방식으로도 처리할 수 없다.
     */
    public static final long MAX_ORIGINAL_BYTES = 100L * 1024 * 1024;

    private MediaConstraints() {
    }
}
