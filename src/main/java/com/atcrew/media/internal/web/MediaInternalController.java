package com.atcrew.media.internal.web;

import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.media.internal.application.OgBackfillService;
import com.atcrew.media.internal.web.dto.ImageProcessedCallbackRequest;
import com.atcrew.media.internal.web.dto.OgProcessedCallbackRequest;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Hidden
@RestController
@RequestMapping("/internal/media")
class MediaInternalController {
    private final MediaCallbackService callbackService; private final OgBackfillService ogBackfillService;
    private final String internalSecret;
    MediaInternalController(MediaCallbackService callbackService, OgBackfillService ogBackfillService,
                            @Value("${artwork.internal.secret}") String internalSecret) {
        this.callbackService = callbackService; this.ogBackfillService = ogBackfillService; this.internalSecret = internalSecret;
    }
    @PostMapping("/images/processed") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleImageProcessed(@RequestHeader("X-Internal-Secret") String secret,
            @RequestBody @Valid ImageProcessedCallbackRequest request) {
        checkSecret(secret);
        callbackService.process(request.ownerType(), request.ownerId(), request.imageKey(), request.thumbKey(),
                request.thumbAdultKey(), request.originalAvifKey(), request.ogKey(), request.ogAdultKey(),
                request.status(), request.failureReason());
    }

    @PostMapping("/images/og-processed") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleOgProcessed(@RequestHeader("X-Internal-Secret") String secret,
            @RequestBody @Valid OgProcessedCallbackRequest request) {
        checkSecret(secret);
        callbackService.attachOg(request.ownerType(), request.ownerId(), request.imageKey(), request.ogKey(),
                request.ogAdultKey());
    }

    /** OG 백필 — 운영 절차는 docs/operations/og-image-backfill.md. Worker에 OG_ONLY 분기가 배포된 뒤에만 부른다. */
    @PostMapping("/og/backfill")
    public OgBackfillResponse backfillOg(@RequestHeader("X-Internal-Secret") String secret,
            @RequestParam(defaultValue = "500") int limit, @RequestParam(defaultValue = "0") long afterId) {
        checkSecret(secret);
        if (limit < 1 || limit > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit은 1~1000이어야 합니다.");
        var result = ogBackfillService.backfill(limit, afterId);
        return new OgBackfillResponse(result.queued(), result.remaining(), result.lastId());
    }

    record OgBackfillResponse(int queued, long remaining, long lastId) { }

    private void checkSecret(String secret) {
        if (!MessageDigest.isEqual(internalSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal secret is invalid");
    }
}
