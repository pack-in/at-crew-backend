package com.atcrew.media.internal.web.dto;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param failureReason 변환이 실패한 이유. Worker가 FAILED일 때만 채워 보내며 구버전 Worker는 아예
 *                      보내지 않으므로 선택 필드다. 저장하지 않고 서버 로그로만 남긴다 — 실패는 드물고,
 *                      필요한 것은 "왜 실패했나"를 되짚는 것이라 컬럼을 늘릴 이유가 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageProcessedCallbackRequest(@NotNull MediaOwnerType ownerType, @NotBlank String ownerId,
                                            @NotBlank String imageKey, String thumbKey, String thumbAdultKey,
                                            String originalAvifKey, @NotNull MediaProcessingStatus status,
                                            String failureReason) { }
