package com.atcrew.auth.internal.web;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.AuthService;
import com.atcrew.auth.EmailLoginCommand;
import com.atcrew.auth.EmailRegisterCommand;
import com.atcrew.auth.GoogleRegisterCommand;
import com.atcrew.auth.internal.web.dto.EmailLoginRequest;
import com.atcrew.auth.internal.web.dto.EmailRegisterRequest;
import com.atcrew.auth.internal.web.dto.GoogleLoginRequest;
import com.atcrew.auth.internal.web.dto.GoogleRegisterRequest;
import com.atcrew.auth.internal.web.dto.RefreshRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "로그인·회원가입·토큰 갱신 API")
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ─── 이메일 인증 ─────────────────────────────────────────────────────

    @Operation(summary = "이메일 로그인",
            description = "이메일·비밀번호로 로그인합니다. 로그인 실패 사유(미가입·탈퇴·비밀번호 오류)는 보안상 단일 오류 코드로 통합됩니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "400", description = "입력 형식 오류")
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    @ApiResponse(responseCode = "428", description = "비밀번호 재설정 필요 (마이그레이션 회원)")
    @ApiResponse(responseCode = "429", description = "로그인 시도 횟수 초과")
    @PostMapping("/email/login")
    public com.atcrew.common.response.ApiResponse<AuthInfo> emailLogin(
            @RequestBody @Valid EmailLoginRequest request) {
        EmailLoginCommand command = new EmailLoginCommand(request.email(), request.password());
        return com.atcrew.common.response.ApiResponse.success(authService.loginWithEmail(command));
    }

    @Operation(summary = "이메일 회원가입",
            description = "이메일·비밀번호로 회원가입합니다. 가입 즉시 활성화됩니다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "입력 형식 오류 또는 비밀번호 불일치")
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/email/register")
    public com.atcrew.common.response.ApiResponse<AuthInfo> emailRegister(
            @RequestBody @Valid EmailRegisterRequest request) {
        EmailRegisterCommand command = new EmailRegisterCommand(
                request.email(), request.password(), request.name(),
                request.agreeService(), request.agreePrivacy(),
                request.agreeThirdParty(), request.agreeMarketing());
        return com.atcrew.common.response.ApiResponse.success(authService.registerWithEmail(command));
    }

    // ─── Google 인증 ─────────────────────────────────────────────────────

    @Operation(summary = "Google 로그인",
            description = "Firebase ID Token으로 Google 로그인합니다. 미가입 시 404를 반환하므로 프론트가 가입 화면으로 이동합니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "Firebase 토큰 검증 실패")
    @ApiResponse(responseCode = "404", description = "미가입 계정 (가입 화면으로 이동)")
    @PostMapping("/google/login")
    public com.atcrew.common.response.ApiResponse<AuthInfo> googleLogin(
            @RequestBody @Valid GoogleLoginRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.loginWithGoogle(request.firebaseIdToken()));
    }

    @Operation(summary = "Google 회원가입",
            description = "Firebase ID Token으로 Google 계정 회원가입합니다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "401", description = "Firebase 토큰 검증 실패")
    @ApiResponse(responseCode = "409", description = "이미 가입된 Google 계정")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/google/register")
    public com.atcrew.common.response.ApiResponse<AuthInfo> googleRegister(
            @RequestBody @Valid GoogleRegisterRequest request) {
        GoogleRegisterCommand command = new GoogleRegisterCommand(
                request.firebaseIdToken(), request.name(),
                request.agreeService(), request.agreePrivacy(),
                request.agreeThirdParty(), request.agreeMarketing());
        return com.atcrew.common.response.ApiResponse.success(authService.registerWithGoogle(command));
    }

    // ─── 공통 ─────────────────────────────────────────────────────────────

    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다.")
    @ApiResponse(responseCode = "200", description = "토큰 갱신 성공")
    @PostMapping("/refresh")
    public com.atcrew.common.response.ApiResponse<AuthInfo> refresh(@RequestBody @Valid RefreshRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.refresh(request.refreshToken()));
    }
}
