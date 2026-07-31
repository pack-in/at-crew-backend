package com.atcrew.recruit.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.recruit.LikedArtistInfo;
import com.atcrew.recruit.RecentlyViewedArtistInfo;
import com.atcrew.recruit.RecruitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관심 작가 API (docs/design/recruit-module-design.md §2.7, §4.3).
 * 기업 계정 전용 기능이지만 기업 인증 게이팅은 스텁이므로 현재는 인증된 회원이면 호출할 수 있다(§7).
 */
@Tag(name = "관심 작가", description = "기업 계정의 작가 좋아요 저장·해제 및 최근 본 작가 조회 API")
@Validated
@RestController
@RequestMapping("/api/recruit")
class LikedArtistController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    // Member ID는 현재 MongoDB ObjectId(24자 hex)이며, member 모듈이 MariaDB로 이관되면 UUID(36자)가 된다 —
    // 두 형식을 모두 허용한다.
    private static final String MEMBER_ID_PATTERN = "^[0-9a-fA-F]{24}$|^[0-9a-fA-F-]{36}$";

    private final RecruitService recruitService;
    private final SecurityUtils securityUtils;

    LikedArtistController(RecruitService recruitService, SecurityUtils securityUtils) {
        this.recruitService = recruitService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "관심 작가 저장", description = "이미 저장된 작가면 최초 저장 시각을 유지합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "저장 성공")
    @PostMapping("/liked-artists/{artistMemberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeArtist(
            @Parameter(description = "작가 Member ID") @PathVariable @Pattern(regexp = MEMBER_ID_PATTERN, message = "작가 ID 형식이 올바르지 않습니다") String artistMemberId) {
        String companyMemberId = securityUtils.getCurrentMemberId();
        recruitService.likeArtist(companyMemberId, artistMemberId);
    }

    @Operation(summary = "관심 작가 해제", description = "저장돼 있지 않아도 성공으로 처리합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "해제 성공")
    @DeleteMapping("/liked-artists/{artistMemberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeArtist(
            @Parameter(description = "작가 Member ID") @PathVariable @Pattern(regexp = MEMBER_ID_PATTERN, message = "작가 ID 형식이 올바르지 않습니다") String artistMemberId) {
        String companyMemberId = securityUtils.getCurrentMemberId();
        recruitService.unlikeArtist(companyMemberId, artistMemberId);
    }

    @Operation(summary = "관심 작가 목록", description =
            "저장 시각 내림차순으로 조회합니다. 검색어 필터는 search 모듈 연동 후 제공됩니다(설계 §2.7).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/liked-artists")
    public ApiResponse<CursorPage<LikedArtistInfo>> getLikedArtists(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String companyMemberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.getLikedArtists(companyMemberId, cursor, resolveSize(size)));
    }

    @Operation(summary = "작가 조회 기록", description =
            "작가 마이페이지 조회 시 최근 본 작가 목록에 기록합니다. 같은 작가를 다시 보면 조회 시각만 갱신됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "기록 성공")
    @PostMapping("/recently-viewed-artists/{artistMemberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // TODO(모듈 연동): 설계 §2.7대로라면 작가 마이페이지 조회 시점에 자동 기록돼야 한다.
    // member → recruit 직접 호출은 순환 의존을 만들므로, member 모듈이 마이페이지 조회 이벤트를 발행하고
    // recruit의 리스너가 이 진입점을 호출하는 방식으로 연결한다(Spring Modulith 이벤트, member 모듈 작업 필요).
    public void recordArtistView(
            @Parameter(description = "작가 Member ID") @PathVariable @Pattern(regexp = MEMBER_ID_PATTERN, message = "작가 ID 형식이 올바르지 않습니다") String artistMemberId) {
        String companyMemberId = securityUtils.getCurrentMemberId();
        recruitService.recordArtistView(companyMemberId, artistMemberId);
    }

    @Operation(summary = "최근 본 작가 목록", description = "조회 시각 내림차순으로 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/recently-viewed-artists")
    public ApiResponse<CursorPage<RecentlyViewedArtistInfo>> getRecentlyViewedArtists(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String companyMemberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.getRecentlyViewedArtists(companyMemberId, cursor, resolveSize(size)));
    }

    private int resolveSize(Integer size) {
        return size != null ? Math.min(size, MAX_SIZE) : DEFAULT_SIZE;
    }
}
