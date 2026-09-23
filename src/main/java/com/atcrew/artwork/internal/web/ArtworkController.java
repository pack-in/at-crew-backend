package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.MaterialData;
import com.atcrew.artwork.PresignedUrlInfo;
import com.atcrew.artwork.UpdateArtworkCommand;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
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
import org.springframework.web.bind.annotation.RequestHeader;
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

    @Operation(summary = "이미지 Presigned URL 발급",
            description = "R2 직접 업로드용 Presigned PUT URL을 발급합니다. fileSizes를 함께 보내면 용량 상한(100MB)을 "
                    + "업로드 시작 전에 검사합니다 — 생략해도 발급되지만 초과분은 이미지 처리 단계에서 실패 처리됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "이미지 개수 오류 또는 count와 목록 수 불일치(INVALID_IMAGE_COUNT), "
                    + "허용되지 않는 형식(INVALID_CONTENT_TYPE), "
                    + "이미지 한 장이 100MB 초과(IMAGE_TOO_LARGE)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
            description = "회원당 발급 한도 초과(PRESIGN_RATE_LIMITED) — 기본 1시간에 300장")
    @PostMapping("/artwork/images/presign")
    public ApiResponse<List<PresignedUrlInfo>> generatePresignedUrls(
            @RequestBody @Valid PresignRequest request) {
        return ApiResponse.success(
                artworkService.generatePresignedUrls(securityUtils.getCurrentMemberId(), request.count(),
                        request.contentTypes(), request.fileSizes()));
    }

    @Operation(summary = "작품 업로드", description = "R2 업로드 완료 후 작품 정보를 저장합니다. 이미지 처리(PROCESSING) 상태로 시작됩니다. "
            + "게시물 작성·노출 언어(languages)는 필수이며 주 사용 언어를 반드시 포함해야 합니다. "
            + "언어를 2개 이상 고르는 것은 프로 플랜 전용입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "업로드 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "게시물 언어 개수 오류(INVALID_LANGUAGE_COUNT), "
                    + "직접입력 값이 10자 초과 또는 항목당 10개 초과(INVALID_CUSTOM_TAG), "
                    + "썸네일(thumbnailKey) 누락(COMMON_INVALID_INPUT), "
                    + "썸네일 key가 작품 이미지 key와 같음(THUMBNAIL_KEY_IN_IMAGES), "
                    + "본인이 발급받지 않은 업로드 key(UNOWNED_IMAGE_KEY), "
                    + "같은 이미지 key 중복(DUPLICATE_IMAGE_KEY)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
            description = "스타터 작품 개수 초과(STARTER_ARTWORK_LIMIT_EXCEEDED), "
                    + "스타터의 다중 언어 선택(MULTI_LANGUAGE_REQUIRES_PRO), "
                    + "주 사용 언어 미포함(LANGUAGE_NOT_ALLOWED)")
    @PostMapping("/artworks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtworkInfo> uploadArtwork(@RequestBody @Valid UploadArtworkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(artworkService.uploadArtwork(memberId, toCommand(request)));
    }

    @Operation(summary = "작품 상세 조회",
            description = "조회수를 올리지 않습니다. 열람 집계는 POST /api/artworks/{artworkId}/views로 별도 호출합니다.")
    @GetMapping("/artworks/{artworkId}")
    public ApiResponse<ArtworkInfo> getArtwork(
            @Parameter(description = "작품 ID") @PathVariable String artworkId) {
        String viewerId = getOptionalMemberId();
        return ApiResponse.success(artworkService.getArtwork(artworkId, viewerId));
    }

    @Operation(summary = "작품 열람 기록",
            description = "작품 상세 화면을 브라우저에서 연 뒤 호출합니다. 인증 선택 — 로그인 회원은 회원 기준으로, "
                    + "비로그인은 X-Anonymous-Id(FE 발급 익명 UUID) 기준으로 기록하며 둘 다 있으면 회원 기준입니다. "
                    + "동일 열람자의 24시간 이내 반복 열람, 본인 작품, 열람할 수 없거나 없는 작품, 식별값이 없는 요청은 "
                    + "기록하지 않지만 응답은 항상 204입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "처리 완료(기록 여부와 무관)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "비로그인 요청의 X-Anonymous-Id가 UUID 형식이 아님(INVALID_ANONYMOUS_ID)")
    @PostMapping("/artworks/{artworkId}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordView(
            @Parameter(description = "작품 ID") @PathVariable String artworkId,
            @Parameter(description = "비로그인 열람자 익명 UUID (FE 1st-party 쿠키 값)")
            @RequestHeader(value = "X-Anonymous-Id", required = false) String anonymousId) {
        artworkService.recordView(artworkId, getOptionalMemberId(), anonymousId);
    }

    @Operation(summary = "작품 처리 상태 폴링", description = "이미지 Worker 처리 완료 여부를 확인합니다.")
    @GetMapping("/artworks/{artworkId}/status")
    public ApiResponse<ArtworkStatus> getArtworkStatus(@PathVariable String artworkId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(artworkService.getArtworkStatus(memberId, artworkId));
    }

    @Operation(summary = "작품 수정", description = "thumbnailKey는 썸네일을 새로 잘라 올린 경우에만 새 key를 보냅니다. "
            + "기존 값을 그대로 보내면 썸네일을 다시 변환하지 않습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "게시물 언어 개수 오류(INVALID_LANGUAGE_COUNT), "
                    + "직접입력 값이 10자 초과 또는 항목당 10개 초과(INVALID_CUSTOM_TAG), "
                    + "썸네일 key가 작품 이미지 key와 같음(THUMBNAIL_KEY_IN_IMAGES), "
                    + "본인이 발급받지 않은 업로드 key(UNOWNED_IMAGE_KEY), "
                    + "같은 이미지 key 중복(DUPLICATE_IMAGE_KEY)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
            description = "스타터의 다중 언어 선택(MULTI_LANGUAGE_REQUIRES_PRO), "
                    + "주 사용 언어 미포함(LANGUAGE_NOT_ALLOWED)")
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
        return ApiResponse.success(artworkService.getMyArtworks(memberId, cursor, resolveSize(size)));
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return 20;
        }
        if (size < 0) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_SIZE);
        }
        return Math.min(size, 50);
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
                req.artworkField(), req.creativeType(), req.roles(), req.genres(), req.customTags(),
                req.tags(), req.ageRating(), req.languages(), req.publishToFeed(), req.portfolioIds(), req.tools(),
                req.workDuration(), req.cutCount(), req.videoLinks(), materials);
    }

    private UpdateArtworkCommand toCommand(UpdateArtworkRequest req) {
        List<MaterialData> materials = req.materials() == null ? null
                : req.materials().stream().map(this::toMaterialData).toList();
        return new UpdateArtworkCommand(
                req.imageKeys(), req.representativeImageIndex(), req.thumbnailKey(),
                req.imageLayoutType(), req.title(), req.description(),
                req.artworkField(), req.creativeType(), req.roles(), req.genres(), req.customTags(),
                req.tags(), req.ageRating(), req.languages(), req.tools(),
                req.workDuration(), req.cutCount(), req.videoLinks(), materials);
    }

    private MaterialData toMaterialData(MaterialRequest r) {
        return new MaterialData(r.name(), r.targets(), r.customTargets(), r.attachmentKeys(), r.links());
    }
}
