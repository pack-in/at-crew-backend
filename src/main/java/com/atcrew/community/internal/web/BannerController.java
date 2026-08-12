package com.atcrew.community.internal.web;

import com.atcrew.community.BannerInfo;
import com.atcrew.community.BannerService;
import com.atcrew.community.CreateBannerCommand;
import com.atcrew.community.UpdateBannerCommand;
import com.atcrew.community.internal.web.dto.CreateBannerRequest;
import com.atcrew.community.internal.web.dto.UpdateBannerRequest;
import com.atcrew.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// TODO: 관리자 권한 체계(RBAC) 도입 전까지 쓰기 엔드포인트는 일반 인증 회원 누구나 호출 가능하다.
// RBAC가 생기면 admin 권한으로 제한해야 한다 (docs/design/community-module-design.md §5.1).
@Tag(name = "커뮤니티 배너", description = "커뮤니티 화면 상단 배너 조회·관리 API — 조회는 공개, "
        + "등록·수정·삭제는 인증 필요(RBAC 도입 전이라 관리자 권한 검사는 없음)")
@RestController
@RequestMapping("/api/community/banners")
class BannerController {

    // 에러 응답 본문은 공통 응답 봉투(code/message)라 성공 응답 스키마가 붙지 않도록 명시적으로 참조한다.
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ApiResponse";

    private final BannerService bannerService;

    BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @Operation(summary = "활성 배너 목록", description =
            "ACTIVE 상태 배너만 노출 순서(sortOrder) 오름차순으로 조회합니다. 페이지네이션 없이 전체를 배열로 반환하며, "
            + "배너가 없으면 빈 배열을 반환합니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ApiResponse<java.util.List<BannerInfo>> getActiveBanners() {
        return ApiResponse.success(bannerService.getActiveBanners());
    }

    @Operation(summary = "배너 등록", description =
            "커뮤니티 상단 배너를 ACTIVE 상태로 등록합니다. sortOrder를 생략하면 마지막 순번 다음 값이 부여되고, "
            + "지정하면 그 순번 이후의 기존 배너가 한 칸씩 뒤로 밀립니다. "
            + "**주의: 관리자 권한 체계(RBAC) 도입 전이라 인증된 회원이면 누구나 호출할 수 있습니다(관리자 권한 검사 없음).** "
            + "요청 본문의 memberId는 호출자와 무관한 값으로, 배너 소유자로 기록되며 실존 회원이어야 합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (UNAUTHENTICATED)",
            content = @Content(mediaType = "*/*", schema = @Schema(ref = ERROR_SCHEMA_REF)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (MEMBER_NOT_FOUND)",
            content = @Content(mediaType = "*/*", schema = @Schema(ref = ERROR_SCHEMA_REF)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BannerInfo> createBanner(@RequestBody @Valid CreateBannerRequest request) {
        return ApiResponse.success(bannerService.createBanner(new CreateBannerCommand(
                request.memberId(), request.imageUrl(), request.linkUrl(), request.sortOrder())));
    }

    @Operation(summary = "배너 수정", description =
            "이미지·링크·노출 순서를 부분 수정합니다. 보내지 않은 필드는 기존 값을 유지하며, 빈 본문({})으로 호출하면 "
            + "변경 없이 현재 값을 그대로 반환합니다. sortOrder를 바꾸면 그 사이 구간의 다른 배너 순번이 함께 조정됩니다. "
            + "**주의: 관리자 권한 체계(RBAC) 도입 전이라 인증된 회원이면 누구나(본인 배너가 아니어도) 호출할 수 있습니다.**")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (UNAUTHENTICATED)",
            content = @Content(mediaType = "*/*", schema = @Schema(ref = ERROR_SCHEMA_REF)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 배너 (BANNER_NOT_FOUND)",
            content = @Content(mediaType = "*/*", schema = @Schema(ref = ERROR_SCHEMA_REF)))
    @PatchMapping("/{bannerId}")
    public ApiResponse<BannerInfo> updateBanner(
            @Parameter(description = "배너 ID (UUIDv7)", example = "019ff382-ccdc-71bb-bccb-6a3c35d33978")
            @PathVariable String bannerId,
            @RequestBody @Valid UpdateBannerRequest request) {
        return ApiResponse.success(bannerService.updateBanner(bannerId, new UpdateBannerCommand(
                request.imageUrl(), request.linkUrl(), request.sortOrder())));
    }

    @Operation(summary = "배너 삭제", description =
            "배너를 DELETED 상태로 변경(soft delete)해 목록에서 제외합니다. 응답 본문은 없습니다. "
            + "이미 삭제된 배너를 다시 삭제해도 204를 반환합니다(멱등). 남은 배너의 sortOrder는 재정렬되지 않아 번호가 비어 있을 수 있습니다. "
            + "**주의: 관리자 권한 체계(RBAC) 도입 전이라 인증된 회원이면 누구나(본인 배너가 아니어도) 호출할 수 있습니다.**")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공 (응답 본문 없음)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (UNAUTHENTICATED)",
            content = @Content(mediaType = "*/*", schema = @Schema(ref = ERROR_SCHEMA_REF)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 배너 (BANNER_NOT_FOUND)",
            content = @Content(mediaType = "*/*", schema = @Schema(ref = ERROR_SCHEMA_REF)))
    @DeleteMapping("/{bannerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBanner(
            @Parameter(description = "배너 ID (UUIDv7)", example = "019ff382-ccdc-71bb-bccb-6a3c35d33978")
            @PathVariable String bannerId) {
        bannerService.deleteBanner(bannerId);
    }
}
