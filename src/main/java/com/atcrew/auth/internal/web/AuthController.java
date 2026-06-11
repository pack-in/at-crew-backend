package com.atcrew.auth.internal.web;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.AuthService;
import com.atcrew.auth.RegisterCommand;
import com.atcrew.auth.internal.web.dto.LoginRequest;
import com.atcrew.auth.internal.web.dto.RefreshRequest;
import com.atcrew.auth.internal.web.dto.RegisterAuthRequest;
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

    @Operation(summary = "로그인",
            description = "Firebase ID Token으로 로그인합니다. 이메일/비밀번호 및 Google 로그인 모두 지원합니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "로그인 실패 (이메일 또는 로그인 방식 오류)")
    @PostMapping("/login")
    public com.atcrew.common.response.ApiResponse<AuthInfo> login(@RequestBody @Valid LoginRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.login(request.firebaseIdToken()));
    }

    @Operation(summary = "회원가입",
            description = "Firebase ID Token으로 회원가입합니다. 이메일 가입과 Google 가입 모두 이 엔드포인트를 사용합니다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register")
    public com.atcrew.common.response.ApiResponse<AuthInfo> register(@RequestBody @Valid RegisterAuthRequest request) {
        RegisterCommand command = new RegisterCommand(
                request.firebaseIdToken(),
                request.name(),
                request.agreePrivacy(),
                request.agreeService(),
                request.agreeMarketing()
        );
        return com.atcrew.common.response.ApiResponse.success(authService.register(command));
    }

    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다.")
    @ApiResponse(responseCode = "200", description = "토큰 갱신 성공")
    @PostMapping("/refresh")
    public com.atcrew.common.response.ApiResponse<AuthInfo> refresh(@RequestBody @Valid RefreshRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.refresh(request.refreshToken()));
    }
}
