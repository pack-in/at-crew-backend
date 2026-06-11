package com.atcrew.member.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.internal.web.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// prod 프로파일에서는 빈 자체가 로드되지 않아 엔드포인트가 존재하지 않음
@Profile("!prod")
@Validated
@Tag(name = "회원 (개발용)", description = "개발·테스트 환경 전용 회원 API")
@RestController
@RequestMapping("/api/members")
class DevMemberController {

    private final MemberService memberService;

    DevMemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "회원 가입 (개발용)", description = "Firebase 인증 없이 이메일·핸들·이름·창작자 유형으로 직접 가입합니다. 개발·테스트 환경 전용.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원 가입 성공"))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberInfo> register(@RequestBody @Valid RegisterRequest request) {
        return ApiResponse.success(
                memberService.register(request.loginEmail(), request.handle(), request.name(), request.creatorRole()));
    }
}
