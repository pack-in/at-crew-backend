package com.atcrew.media.internal.web;

import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.media.internal.web.dto.ImageProcessedCallbackRequest;
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
    private final MediaCallbackService callbackService; private final String internalSecret;
    MediaInternalController(MediaCallbackService callbackService, @Value("${artwork.internal.secret}") String internalSecret) {
        this.callbackService = callbackService; this.internalSecret = internalSecret;
    }
    @PostMapping("/images/processed") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleImageProcessed(@RequestHeader("X-Internal-Secret") String secret,
            @RequestBody @Valid ImageProcessedCallbackRequest request) {
        if (!MessageDigest.isEqual(internalSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal secret is invalid");
        callbackService.process(request.ownerType(), request.ownerId(), request.imageKey(), request.thumbKey(),
                request.thumbAdultKey(), request.originalAvifKey(), request.status());
    }
}
