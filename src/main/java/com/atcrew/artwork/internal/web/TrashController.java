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

    @Operation(summary = "휴지통 목록",
            description = """
                    휴지통(status=DELETED)에 있는 내 작품을 업로드 시각 내림차순으로 조회합니다.

                    휴지통 작품의 공개 범위는 항상 PRIVATE로 표시되며, 삭제 직전 값은 복구 시 되돌아갑니다.
                    커서 페이지네이션이며 다음 페이지는 응답의 nextCursor를 cursor로 그대로 넘겨 요청합니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_CURSOR(커서 형식 오류)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/artworks")
    public ApiResponse<CursorPage<ArtworkSummaryInfo>> getTrashArtworks(
            @Parameter(description = "커서 — 직전 페이지 응답의 nextCursor(마지막 작품 createdAt의 epoch milli). "
                    + "첫 페이지는 생략합니다", example = "1786496887918")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50 — 초과 값은 50으로 잘립니다)", example = "20")
            @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        int pageSize = size != null ? Math.min(size, 50) : 20;
        return ApiResponse.success(artworkService.getTrashArtworks(memberId, cursor, pageSize));
    }

    @Operation(summary = "작품 복구",
            description = """
                    선택한 작품을 휴지통에서 복구합니다. 응답 본문은 없습니다.

                    상태가 READY로 돌아가고 공개 범위는 삭제 직전 값으로 복원됩니다(보관된 값이 없으면 PRIVATE).
                    요청한 ID가 전부 존재하고 본인 소유이며 휴지통에 있어야 하고, 하나라도 어긋나면 아무것도 복구되지 않습니다 —
                    존재하지 않는 ID가 섞여 있으면 404, 휴지통에 없는 작품이면 400 ARTWORK_NOT_DELETED입니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "복구 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "ARTWORK_NOT_DELETED(휴지통에 있는 작품이 아님), COMMON_INVALID_INPUT(artworkIds가 비어 있음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ARTWORK_ACCESS_DENIED(본인 작품이 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ARTWORK_NOT_FOUND(존재하지 않는 작품이 포함됨)")
    })
    @PostMapping("/artworks/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreArtworks(@RequestBody @Valid RestoreArtworkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        artworkService.restoreArtworks(memberId, request.artworkIds());
    }

    @Operation(summary = "작품 영구 삭제",
            description = """
                    휴지통의 작품을 영구 삭제합니다. 응답 본문은 없으며 되돌릴 수 없습니다.

                    작품 레코드와 함께 R2에 저장된 원본·썸네일 파일도 정리 대상이 되고, 이후 상세 조회는 404 ARTWORK_NOT_FOUND입니다.
                    요청한 ID가 전부 존재하고 본인 소유이며 휴지통에 있어야 하며, 휴지통에 없는 작품이 섞여 있으면
                    400 ARTWORK_NOT_DELETED로 아무것도 삭제되지 않습니다 — 먼저 작품 삭제 API로 휴지통에 넣어야 합니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "ARTWORK_NOT_DELETED(휴지통에 있는 작품이 아님), COMMON_INVALID_INPUT(artworkIds가 비어 있음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ARTWORK_ACCESS_DENIED(본인 작품이 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ARTWORK_NOT_FOUND(존재하지 않는 작품이 포함됨)")
    })
    @DeleteMapping("/artworks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentlyDeleteArtworks(@RequestBody @Valid DeleteArtworkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        artworkService.permanentlyDeleteArtworks(memberId, request.artworkIds());
    }
}
