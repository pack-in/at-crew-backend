package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "커뮤니티", description = "커뮤니티 작품 피드 API")
@RestController
@RequestMapping("/api/community")
class CommunityController {

    private final ArtworkService artworkService;

    CommunityController(ArtworkService artworkService) {
        this.artworkService = artworkService;
    }

    @Operation(summary = "커뮤니티 작품 목록", description = "공개 작품을 최신순으로 조회합니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/artworks")
    public ApiResponse<CursorPage<ArtworkSummaryInfo>> getCommunityArtworks(
            @Parameter(description = "작품 분야 필터") @RequestParam(required = false) ArtworkField artworkField,
            @Parameter(description = "연령 등급 필터 (기본 ALL)") @RequestParam(required = false) AgeRating ageRating,
            @Parameter(description = "커서 (마지막 작품 createdAt millis)") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        int pageSize = size != null ? Math.min(size, 50) : 20;
        return ApiResponse.success(artworkService.getCommunityArtworks(artworkField, ageRating, cursor, pageSize));
    }
}
