package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.artwork.internal.web.dto.DeleteArtworkRequest;
import com.atcrew.artwork.internal.web.dto.RestoreArtworkRequest;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "휴지통", description = "휴지통 작품 복구·영구 삭제 API")
@RestController
@RequestMapping("/api/trash")
class TrashController {

    private final ArtworkService artworkService;
    private final SecurityUtils securityUtils;

    TrashController(ArtworkService artworkService, SecurityUtils securityUtils) {
        this.artworkService = artworkService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "휴지통 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/artworks")
    public ApiResponse<CursorPage<ArtworkSummaryInfo>> getTrashArtworks(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        int pageSize = size != null ? Math.min(size, 50) : 20;
        return ApiResponse.success(artworkService.getTrashArtworks(memberId, cursor, pageSize));
    }

    @Operation(summary = "작품 복구", description = "선택한 작품을 휴지통에서 복구합니다. 삭제 전 공개 상태가 복원됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "복구 성공")
    @PostMapping("/artworks/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreArtworks(@RequestBody @Valid RestoreArtworkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        artworkService.restoreArtworks(memberId, request.artworkIds());
    }

    @Operation(summary = "작품 영구 삭제", description = "휴지통의 작품을 영구 삭제합니다. R2 원본 파일도 삭제됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/artworks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentlyDeleteArtworks(@RequestBody @Valid DeleteArtworkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        artworkService.permanentlyDeleteArtworks(memberId, request.artworkIds());
    }
}
