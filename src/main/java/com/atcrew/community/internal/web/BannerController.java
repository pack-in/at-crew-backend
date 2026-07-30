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
@Tag(name = "커뮤니티 배너", description = "커뮤니티 화면 상단 배너 조회·관리 API")
@RestController
@RequestMapping("/api/community/banners")
class BannerController {

    private final BannerService bannerService;

    BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @Operation(summary = "활성 배너 목록", description = "노출 순서(sortOrder) 오름차순으로 활성 배너 목록을 조회합니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ApiResponse<java.util.List<BannerInfo>> getActiveBanners() {
        return ApiResponse.success(bannerService.getActiveBanners());
    }

    @Operation(summary = "배너 등록", description = "커뮤니티 상단 배너를 등록합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BannerInfo> createBanner(@RequestBody @Valid CreateBannerRequest request) {
        return ApiResponse.success(bannerService.createBanner(new CreateBannerCommand(
                request.memberId(), request.imageUrl(), request.linkUrl(), request.sortOrder())));
    }

    @Operation(summary = "배너 수정", description = "이미지·링크·노출 순서를 수정합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @PatchMapping("/{bannerId}")
    public ApiResponse<BannerInfo> updateBanner(
            @Parameter(description = "배너 ID") @PathVariable String bannerId,
            @RequestBody @Valid UpdateBannerRequest request) {
        return ApiResponse.success(bannerService.updateBanner(bannerId, new UpdateBannerCommand(
                request.imageUrl(), request.linkUrl(), request.sortOrder())));
    }

    @Operation(summary = "배너 삭제", description = "배너를 삭제(soft delete)합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/{bannerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBanner(@Parameter(description = "배너 ID") @PathVariable String bannerId) {
        bannerService.deleteBanner(bannerId);
    }
}
