package com.atcrew.auth.internal.web;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.AuthService;
import com.atcrew.auth.internal.web.dto.LoginRequest;
import com.atcrew.auth.internal.web.dto.RefreshRequest;
import com.atcrew.common.response.CommonApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "Firebase Google 소셜 로그인 및 토큰 갱신 API")
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "로그인 / 회원가입", description = "Firebase ID Token으로 인증합니다. 신규 사용자는 자동으로 가입됩니다.")
    @ApiResponse(responseCode = "200", description = "인증 성공")
    @CommonApiResponses
    @PostMapping("/login")
    public com.atcrew.common.response.ApiResponse<AuthInfo> login(@RequestBody @Valid LoginRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.login(request.firebaseIdToken()));
    }

    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다.")
    @ApiResponse(responseCode = "200", description = "토큰 갱신 성공")
    @CommonApiResponses
    @PostMapping("/refresh")
    public com.atcrew.common.response.ApiResponse<AuthInfo> refresh(@RequestBody @Valid RefreshRequest request) {
        return com.atcrew.common.response.ApiResponse.success(authService.refresh(request.refreshToken()));
    }
}
