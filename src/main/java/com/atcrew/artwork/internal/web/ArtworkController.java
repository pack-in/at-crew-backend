package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.MaterialData;
import com.atcrew.artwork.PresignedUrlInfo;
import com.atcrew.artwork.UpdateArtworkCommand;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.internal.web.dto.MaterialRequest;
import com.atcrew.artwork.internal.web.dto.PresignRequest;
import com.atcrew.artwork.internal.web.dto.UpdateArtworkRequest;
import com.atcrew.artwork.internal.web.dto.UpdatePublicationRequest;
import com.atcrew.artwork.internal.web.dto.UploadArtworkRequest;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "작품", description = "작품 업로드·조회·수정·삭제 API")
@Validated
@RestController
@RequestMapping("/api")
class ArtworkController {

    private final ArtworkService artworkService;
    private final SecurityUtils securityUtils;

    ArtworkController(ArtworkService artworkService, SecurityUtils securityUtils) {
        this.artworkService = artworkService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "이미지 Presigned URL 발급", description = "R2 직접 업로드용 Presigned PUT URL을 발급합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공")
    @PostMapping("/artwork/images/presign")
    public ApiResponse<List<PresignedUrlInfo>> generatePresignedUrls(
            @RequestBody @Valid PresignRequest request) {
        return ApiResponse.success(
                artworkService.generatePresignedUrls(request.count(), request.contentTypes()));
    }

    @Operation(summary = "작품 업로드", description = "R2 업로드 완료 후 작품 정보를 저장합니다. 이미지 처리(PROCESSING) 상태로 시작됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "업로드 성공")
    @PostMapping("/artworks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtworkInfo> uploadArtwork(@RequestBody @Valid UploadArtworkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(artworkService.uploadArtwork(memberId, toCommand(request)));
    }

    @Operation(summary = "작품 상세 조회")
    @GetMapping("/artworks/{artworkId}")
    public ApiResponse<ArtworkInfo> getArtwork(
            @Parameter(description = "작품 ID") @PathVariable String artworkId) {
        String viewerId = getOptionalMemberId();
        return ApiResponse.success(artworkService.getArtwork(artworkId, viewerId));
    }

    @Operation(summary = "작품 처리 상태 폴링", description = "이미지 Worker 처리 완료 여부를 확인합니다.")
    @GetMapping("/artworks/{artworkId}/status")
    public ApiResponse<ArtworkStatus> getArtworkStatus(@PathVariable String artworkId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(artworkService.getArtworkStatus(memberId, artworkId));
    }

    @Operation(summary = "작품 수정")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @PatchMapping("/artworks/{artworkId}")
    public ApiResponse<ArtworkInfo> updateArtwork(@PathVariable String artworkId,
                                                   @RequestBody @Valid UpdateArtworkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(artworkService.updateArtwork(memberId, artworkId, toCommand(request)));
    }

    @Operation(summary = "노출 위치 재선언",
            description = "작품 피드 공개 여부와 담을 포트폴리오를 함께 재선언합니다. 공개 상태는 이 조합으로 "
                    + "서버가 계산하며, portfolioIds는 증분이 아니라 전체 목록이라 빠진 포트폴리오에서는 제외됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "변경 성공")
    @PatchMapping("/artworks/{artworkId}/publication")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePublication(@PathVariable String artworkId,
                                  @RequestBody @Valid UpdatePublicationRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        artworkService.updatePublication(memberId, artworkId, request.publishToFeed(), request.portfolioIds());
    }

    @Operation(summary = "작품 삭제 (휴지통 이동)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/artworks/{artworkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteArtwork(@PathVariable String artworkId) {
        String memberId = securityUtils.getCurrentMemberId();
        artworkService.deleteArtwork(memberId, artworkId);
    }

    @Operation(summary = "내 작품 목록")
    @GetMapping("/members/me/artworks")
    public ApiResponse<com.atcrew.common.response.CursorPage<com.atcrew.artwork.ArtworkSummaryInfo>> getMyArtworks(
            @Parameter(description = "커서 (마지막 작품 createdAt millis)") String cursor,
            @Parameter(description = "페이지 크기 (기본 20)") Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        int pageSize = size != null ? Math.min(size, 50) : 20;
        return ApiResponse.success(artworkService.getMyArtworks(memberId, cursor, pageSize));
    }

    private String getOptionalMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof com.atcrew.common.security.MemberPrincipal p) {
            return p.memberId();
        }
        return null;
    }

    private UploadArtworkCommand toCommand(UploadArtworkRequest req) {
        List<MaterialData> materials = req.materials() == null ? List.of()
                : req.materials().stream().map(this::toMaterialData).toList();
        return new UploadArtworkCommand(
                req.imageKeys(), req.representativeImageIndex(), req.thumbnailKey(),
                req.imageLayoutType(), req.title(), req.description(),
                req.artworkField(), req.creativeType(), req.roles(), req.genres(),
                req.tags(), req.ageRating(), req.publishToFeed(), req.portfolioIds(), req.tools(),
                req.workDuration(), req.cutCount(), req.videoLinks(), materials);
    }

    private UpdateArtworkCommand toCommand(UpdateArtworkRequest req) {
        List<MaterialData> materials = req.materials() == null ? null
                : req.materials().stream().map(this::toMaterialData).toList();
        return new UpdateArtworkCommand(
                req.imageKeys(), req.representativeImageIndex(), req.thumbnailKey(),
                req.imageLayoutType(), req.title(), req.description(),
                req.artworkField(), req.creativeType(), req.roles(), req.genres(),
                req.tags(), req.ageRating(), req.tools(),
                req.workDuration(), req.cutCount(), req.videoLinks(), materials);
    }

    private MaterialData toMaterialData(MaterialRequest r) {
        return new MaterialData(r.name(), r.targets(), r.attachmentKeys(), r.links());
    }
}
