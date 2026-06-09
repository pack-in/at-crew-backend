package com.atcrew.member.internal.web;

import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.internal.web.dto.AddCareerRequest;
import com.atcrew.member.internal.web.dto.RegisterRequest;
import com.atcrew.member.internal.web.dto.UpdateInfoRequest;
import com.atcrew.member.internal.web.dto.UpdateNameRequest;
import com.atcrew.common.CommonApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
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

@Tag(name = "회원", description = "회원 가입·조회·프로필 수정·경력 관리 API")
@Validated
@RestController
@RequestMapping("/api/members")
class MemberController {

    private final MemberService memberService;

    MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "회원 가입", description = "이메일·핸들·이름·창작자 유형으로 회원을 등록합니다.")
    @ApiResponse(responseCode = "201", description = "회원 가입 성공")
    @CommonApiResponses
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberInfo register(@RequestBody @Valid RegisterRequest request) {
        return memberService.register(request.loginEmail(), request.handle(), request.name(), request.creatorRole());
    }

    @Operation(summary = "핸들로 회원 조회", description = "@핸들로 회원 프로필을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @CommonApiResponses
    @GetMapping("/{handle}")
    public MemberInfo findByHandle(
            @Parameter(description = "회원 핸들 (@ 제외)", example = "creator_kim")
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{3,30}$", message = "핸들 형식이 올바르지 않습니다") String handle) {
        return memberService.findByHandle(handle);
    }

    @Operation(summary = "이름 수정", description = "회원의 이름·작가명을 수정합니다. (최대 16자)")
    @ApiResponse(responseCode = "204", description = "수정 성공")
    @CommonApiResponses
    @PatchMapping("/{id}/name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateName(
            @Parameter(description = "회원 ID") @PathVariable String id,
            @RequestBody @Valid UpdateNameRequest request) {
        memberService.updateName(id, request.name());
    }

    @Operation(summary = "프로필 정보 수정", description = "구인구직 상태·활동 분야·경력·지역·슬롯·연락처·SNS·툴 등 프로필 전체를 수정합니다.")
    @ApiResponse(responseCode = "204", description = "수정 성공")
    @CommonApiResponses
    @PatchMapping("/{id}/info")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateInfo(
            @Parameter(description = "회원 ID") @PathVariable String id,
            @RequestBody @Valid UpdateInfoRequest request) {
        memberService.updateInfo(id, new UpdateInfoCommand(
                request.creatorRole(), request.employmentStatus(),
                request.activityFields(), request.experienceLevel(), request.activeRegions(),
                request.totalSlotCount(), request.availableSlotCount(), request.teamExperiences(),
                request.contact(), request.sns(), request.tools()));
    }

    @Operation(summary = "경력 추가", description = "참여작 정보를 경력으로 추가합니다.")
    @ApiResponse(responseCode = "201", description = "경력 추가 성공")
    @CommonApiResponses
    @PostMapping("/{id}/careers")
    @ResponseStatus(HttpStatus.CREATED)
    public CareerEntryInfo addCareer(
            @Parameter(description = "회원 ID") @PathVariable String id,
            @RequestBody @Valid AddCareerRequest request) {
        return memberService.addCareer(id, new AddCareerCommand(
                request.workTitle(), request.role(), request.startDate(),
                request.endDate(), request.ongoing(), request.description()));
    }

    @Operation(summary = "경력 삭제", description = "등록된 경력 항목을 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @CommonApiResponses
    @DeleteMapping("/{id}/careers/{careerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCareer(
            @Parameter(description = "회원 ID") @PathVariable String id,
            @Parameter(description = "경력 ID") @PathVariable String careerId) {
        memberService.deleteCareer(id, careerId);
    }

    @Operation(summary = "회원 탈퇴", description = "회원을 비활성화(소프트 딜리트) 처리합니다.")
    @ApiResponse(responseCode = "204", description = "탈퇴 처리 성공")
    @CommonApiResponses
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@Parameter(description = "회원 ID") @PathVariable String id) {
        memberService.deactivate(id);
    }
}
