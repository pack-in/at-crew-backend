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
import com.atcrew.artwork.internal.web.dto.UpdateVisibilityRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
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
            description = """
                    작품 이미지를 R2에 직접 올리기 위한 Presigned PUT URL을 요청한 개수만큼 발급합니다.

                    발급된 uploadUrl로 이미지를 PUT 업로드한 뒤, 응답의 key를 작품 업로드·수정 요청의 imageKeys에 순서대로 넣습니다.
                    URL 유효 시간은 10분이며, 업로드 시 Content-Type 헤더를 발급 때 지정한 값과 동일하게 보내야 합니다.
                    허용 형식은 image/jpeg, image/png, image/webp 세 가지입니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_IMAGE_COUNT(count와 contentTypes 길이 불일치), "
                            + "INVALID_CONTENT_TYPE(허용되지 않는 이미지 형식), "
                            + "COMMON_INVALID_INPUT(count가 1~20 범위 밖이거나 contentTypes가 비어 있음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "PRESIGN_FAILED(업로드 URL 생성 실패)")
    })
    @PostMapping("/artwork/images/presign")
    public ApiResponse<List<PresignedUrlInfo>> generatePresignedUrls(
            @RequestBody @Valid PresignRequest request) {
        return ApiResponse.success(
                artworkService.generatePresignedUrls(request.count(), request.contentTypes()));
    }

    @Operation(summary = "작품 업로드",
            description = """
                    R2 업로드를 마친 이미지 key로 작품을 생성합니다.

                    생성 직후 상태는 PROCESSING이며, 이미지 변환이 백그라운드로 진행됩니다.
                    모든 이미지 처리가 끝나고 하나 이상 성공하면 READY로 전이하고, 그때부터 공개 상태 변경·북마크 대상이 됩니다.
                    전이 여부는 작품 처리 상태 폴링 API로 확인합니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "업로드 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_REPRESENTATIVE_INDEX(대표 이미지 인덱스가 imageKeys 범위 밖), "
                            + "COMMON_INVALID_INPUT(필수값 누락, imageKeys가 비었거나 20개 초과 등 입력값 유효성 오류)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/artworks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtworkInfo> uploadArtwork(@RequestBody @Valid UploadArtworkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(artworkService.uploadArtwork(memberId, toCommand(request)));
    }

    @Operation(summary = "작품 상세 조회",
            description = """
                    작품 상세 정보를 조회합니다. 로그인 없이도 호출할 수 있으며, 토큰을 보내면 본인 작품 여부에 따라 접근 범위가 달라집니다.

                    - 본인 작품: 상태·공개 범위와 무관하게 항상 조회됩니다(PROCESSING·휴지통 작품 포함).
                    - 타인 작품이 휴지통에 있으면 410 ARTWORK_DELETED.
                    - 타인 작품이 아직 READY가 아니면 존재를 노출하지 않기 위해 404 ARTWORK_NOT_FOUND.
                    - 타인 작품이 READY이고 공개 범위가 PUBLIC 또는 LINK_ONLY면 조회됩니다.
                    - 타인 작품이 READY이고 PRIVATE면 라이브 포트폴리오에 포함된 경우에만 조회되고, 아니면 403 ARTWORK_PRIVATE.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ARTWORK_PRIVATE(비공개 작품)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ARTWORK_NOT_FOUND(존재하지 않거나 아직 공개되지 않은 작품)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410",
                    description = "ARTWORK_DELETED(휴지통으로 이동된 작품)")
    })
    @GetMapping("/artworks/{artworkId}")
    public ApiResponse<ArtworkInfo> getArtwork(
            @Parameter(description = "작품 ID", example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
            @PathVariable String artworkId) {
        String viewerId = getOptionalMemberId();
        return ApiResponse.success(artworkService.getArtwork(artworkId, viewerId));
    }

    @Operation(summary = "작품 처리 상태 폴링",
            description = """
                    업로드·이미지 교체 후 이미지 변환이 끝났는지 확인합니다. 본인 작품만 조회할 수 있습니다.

                    응답 data는 작품 상태 문자열입니다 — PROCESSING(처리 중), READY(처리 완료), DELETED(휴지통).
                    PROCESSING인 동안 주기적으로 재호출하고, READY가 되면 공개 상태 변경 API를 호출할 수 있습니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ARTWORK_ACCESS_DENIED(본인 작품이 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ARTWORK_NOT_FOUND(존재하지 않는 작품)")
    })
    @GetMapping("/artworks/{artworkId}/status")
    public ApiResponse<ArtworkStatus> getArtworkStatus(
            @Parameter(description = "작품 ID", example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
            @PathVariable String artworkId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(artworkService.getArtworkStatus(memberId, artworkId));
    }

    @Operation(summary = "작품 수정",
            description = """
                    본인 작품의 내용을 부분 수정하고 수정된 작품 상세를 반환합니다. 보낸 필드만 반영되며, 생략한 필드는 기존 값이 유지됩니다.

                    imageKeys를 보내면 이미지 전체가 교체되고 작품 상태가 다시 PROCESSING으로 돌아갑니다.
                    공개 범위는 이 API로 변경할 수 없으며 공개 상태 변경 API를 사용합니다.
                    휴지통에 있는 작품은 수정할 수 없습니다(404 ARTWORK_NOT_FOUND).""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_IMAGE_COUNT(imageKeys를 빈 배열로 보냄), "
                            + "INVALID_REPRESENTATIVE_INDEX(대표 이미지 인덱스가 이미지 목록 범위 밖), "
                            + "COMMON_INVALID_INPUT(imageKeys 20개 초과 등 입력값 유효성 오류)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ARTWORK_ACCESS_DENIED(본인 작품이 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ARTWORK_NOT_FOUND(존재하지 않거나 휴지통에 있는 작품)")
    })
    @PatchMapping("/artworks/{artworkId}")
    public ApiResponse<ArtworkInfo> updateArtwork(
            @Parameter(description = "작품 ID", example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
            @PathVariable String artworkId,
            @RequestBody @Valid UpdateArtworkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(artworkService.updateArtwork(memberId, artworkId, toCommand(request)));
    }

    @Operation(summary = "공개 상태 변경",
            description = """
                    본인 작품의 공개 범위를 PUBLIC(전체 공개)·LINK_ONLY(링크 공개)·PRIVATE(비공개) 중 하나로 변경합니다. 응답 본문은 없습니다.

                    작품 상태가 READY일 때만 허용되며, 이미지 처리 중(PROCESSING)이거나 휴지통(DELETED)에 있으면
                    400 ARTWORK_NOT_READY가 반환됩니다. 업로드 직후에는 처리 상태 폴링으로 READY 전이를 확인한 뒤 호출하세요.
                    작품 업로드 시점의 공개 범위는 업로드 요청의 visibility로 지정합니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "ARTWORK_NOT_READY(이미지 처리 중이거나 휴지통에 있는 작품), COMMON_INVALID_INPUT(입력값 유효성 오류)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ARTWORK_ACCESS_DENIED(본인 작품이 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ARTWORK_NOT_FOUND(존재하지 않는 작품)")
    })
    @PatchMapping("/artworks/{artworkId}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateVisibility(
            @Parameter(description = "작품 ID", example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
            @PathVariable String artworkId,
            @RequestBody @Valid UpdateVisibilityRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        artworkService.updateVisibility(memberId, artworkId, request.visibility());
    }

    @Operation(summary = "작품 삭제 (휴지통 이동)",
            description = """
                    본인 작품을 휴지통으로 옮깁니다(소프트 삭제). 응답 본문은 없습니다.

                    상태가 DELETED로 바뀌고 공개 범위는 PRIVATE로 강제 변경되며, 삭제 직전 공개 범위는 복구 시 되돌리기 위해 보관됩니다.
                    이후 내 작품 목록에서는 빠지고 휴지통 목록에 나타납니다. 이미 휴지통에 있는 작품에 다시 호출해도 204입니다(멱등).
                    완전히 지우려면 휴지통 영구 삭제 API를 호출합니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ARTWORK_ACCESS_DENIED(본인 작품이 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ARTWORK_NOT_FOUND(존재하지 않는 작품)")
    })
    @DeleteMapping("/artworks/{artworkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteArtwork(
            @Parameter(description = "작품 ID", example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
            @PathVariable String artworkId) {
        String memberId = securityUtils.getCurrentMemberId();
        artworkService.deleteArtwork(memberId, artworkId);
    }

    @Operation(summary = "내 작품 목록",
            description = """
                    내가 업로드한 작품을 최신순(업로드 시각 내림차순)으로 조회합니다. 휴지통에 있는 작품은 제외되며, 휴지통 목록 API로 조회합니다.

                    커서 페이지네이션이며, 다음 페이지는 응답의 nextCursor를 cursor로 그대로 넘겨 요청합니다.
                    hasNext가 false면 마지막 페이지입니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_CURSOR(커서 형식 오류)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/members/me/artworks")
    public ApiResponse<com.atcrew.common.response.CursorPage<com.atcrew.artwork.ArtworkSummaryInfo>> getMyArtworks(
            @Parameter(description = "커서 — 직전 페이지 응답의 nextCursor(마지막 작품 createdAt의 epoch milli). "
                    + "첫 페이지는 생략합니다", example = "1786496887918")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50 — 초과 값은 50으로 잘립니다)", example = "20")
            @RequestParam(required = false) Integer size) {
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
                req.tags(), req.ageRating(), req.visibility(), req.tools(),
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
