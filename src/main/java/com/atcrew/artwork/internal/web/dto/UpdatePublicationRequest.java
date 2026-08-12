package com.atcrew.artwork.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 작품 노출 위치 재선언 요청 (업로드-R09).
 *
 * <p>증분 변경이 아니라 전체 재선언이다 — {@code portfolioIds}에 없는 기존 편입은 해제된다.
 */
public record UpdatePublicationRequest(
        @NotNull Boolean publishToFeed,                        // 앳크루 작품 피드에 공개할지
        List<@NotBlank @Size(max = 36) String> portfolioIds    // 담아둘 라이브 포트폴리오 ID 전체 목록
) {
}
