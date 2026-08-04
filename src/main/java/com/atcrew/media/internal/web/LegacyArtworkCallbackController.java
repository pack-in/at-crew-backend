package com.atcrew.media.internal.web;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.media.internal.web.dto.LegacyArtworkCallbackRequest;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 구 경로 {@code /internal/artwork/images/processed} shim — Cloudflare Worker가 아직 {@code artworkId}
 * 형식으로 콜백을 보내는 전환 기간에만 존재한다(docs/design/media-module-design.md §9.2 롤아웃 1단계).
 * 서버를 먼저 관대하게 배포해야 Worker 배포 순서 때문에 기존 artwork 콜백이 끊기지 않는다.
 *
 * <p>Worker가 새 형식으로 완전히 전환된 것을 확인한 뒤(3단계) 이 컨트롤러와 DTO,
 * {@code SecurityConfig}의 구 경로 permitAll을 함께 제거한다.
 */
@Hidden
@RestController
@RequestMapping("/internal/artwork")
class LegacyArtworkCallbackController {

    private final MediaCallbackService callbackService;
    private final String internalSecret;

    LegacyArtworkCallbackController(MediaCallbackService callbackService,
                                    @Value("${artwork.internal.secret}") String internalSecret) {
        this.callbackService = callbackService;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/images/processed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleImageProcessed(@RequestHeader("X-Internal-Secret") String secret,
                                     @RequestBody @Valid LegacyArtworkCallbackRequest request) {
        if (!MessageDigest.isEqual(internalSecret.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal secret is invalid");
        }
        callbackService.process(MediaOwnerType.ARTWORK, request.artworkId(), request.imageKey(),
                request.thumbKey(), request.thumbAdultKey(), request.originalAvifKey(), request.status());
    }
}
