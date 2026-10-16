package com.atcrew.media.internal.web.dto;

import com.atcrew.media.MediaOwnerType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * OG 전용 처리(백필, variantProfile=OG_ONLY) 결과 콜백. 썸네일 처리 콜백과 엔드포인트를 나눈 이유는 이 콜백이 썸네일
 * 필드를 싣지 않기 때문이다 — 같은 경로로 받으면 썸네일 key가 null로 덮인다.
 *
 * @param imageKey   자산 식별용 원본 key(raw는 이미 지워졌어도 행의 original_key로 남아 있다).
 * @param ogKey      생성에 실패했으면 null.
 * @param ogAdultKey 생성에 실패했으면 null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OgProcessedCallbackRequest(@NotNull MediaOwnerType ownerType, @NotBlank String ownerId,
                                         @NotBlank String imageKey, String ogKey, String ogAdultKey) { }
