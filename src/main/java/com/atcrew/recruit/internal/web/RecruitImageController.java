package com.atcrew.recruit.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.media.MediaConstraints;
import com.atcrew.media.MediaService;
import com.atcrew.media.PresignedUrlInfo;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import com.atcrew.recruit.internal.web.dto.PresignRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 구인글·팀원모집글·구직글 이미지 업로드용 Presigned URL 발급 (docs/design/media-module-design.md §10.3).
 * 발급 로직은 도메인과 무관한 범용 작업이라 media 모듈에 그대로 위임한다.
 */
@Tag(name = "구인/구직 이미지", description = "구인글·팀원모집글·구직글 이미지 업로드 API")
@Validated
@RestController
@RequestMapping("/api/recruit")
class RecruitImageController {

    private final MediaService mediaService;

    RecruitImageController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Operation(summary = "이미지 Presigned URL 발급",
            description = "R2 직접 업로드용 Presigned PUT URL을 발급합니다. 게시글 작성·수정 시 thumbnailImage/"
                    + "referenceImages에는 여기서 받은 key를 넣습니다. fileSizes를 함께 보내면 용량 상한(100MB)을 "
                    + "업로드 시작 전에 검사합니다 — 생략해도 발급되지만 초과분은 이미지 처리 단계에서 실패 처리됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "이미지 한 장이 100MB 초과(IMAGE_TOO_LARGE)")
    @PostMapping("/images/presign")
    public ApiResponse<List<PresignedUrlInfo>> generatePresignedUrls(@RequestBody @Valid PresignRequest request) {
        // media는 IllegalArgumentException을 던지므로 그대로 흘리면 400이 500으로 바뀐다 — artwork와 같은
        // 이유로 크기 검증만 여기서 먼저 한다(개수·형식 검증은 기존대로 media에 맡긴다).
        if (request.fileSizes() != null) {
            for (Long size : request.fileSizes()) {
                if (size != null && size > MediaConstraints.MAX_ORIGINAL_BYTES) {
                    throw new RecruitException(RecruitErrorCode.IMAGE_TOO_LARGE, size + "바이트");
                }
            }
        }
        return ApiResponse.success(
                mediaService.generatePresignedUrls(request.count(), request.contentTypes(), request.fileSizes()));
    }
}
