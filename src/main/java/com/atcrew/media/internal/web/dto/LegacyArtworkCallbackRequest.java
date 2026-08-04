package com.atcrew.media.internal.web.dto;

import com.atcrew.media.MediaProcessingStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 구 Worker 콜백 형식({@code artworkId}) — 전환 기간 한정
 * (docs/design/media-module-design.md §9.2 롤아웃 1단계).
 * Worker가 새 형식으로 완전히 전환되면 이 DTO와 {@code LegacyArtworkCallbackController}를 함께 삭제한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LegacyArtworkCallbackRequest(@NotBlank String artworkId, @NotBlank String imageKey,
                                           String thumbKey, String thumbAdultKey, String originalAvifKey,
                                           @NotNull MediaProcessingStatus status) { }
