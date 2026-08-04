package com.atcrew.recruit.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.media.MediaService;
import com.atcrew.media.PresignedUrlInfo;
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
                    + "referenceImages에는 여기서 받은 key를 넣습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공")
    @PostMapping("/images/presign")
    public ApiResponse<List<PresignedUrlInfo>> generatePresignedUrls(@RequestBody @Valid PresignRequest request) {
        return ApiResponse.success(mediaService.generatePresignedUrls(request.count(), request.contentTypes()));
    }
}
