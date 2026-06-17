package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ImageProcessedCallbackCommand;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.artwork.internal.web.dto.ImageProcessedCallbackRequest;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/artwork")
class ArtworkInternalController {

    private final ArtworkService artworkService;
    private final String internalSecret;

    ArtworkInternalController(ArtworkService artworkService,
                               @Value("${artwork.internal.secret}") String internalSecret) {
        this.artworkService = artworkService;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/images/processed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleImageProcessed(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody @Valid ImageProcessedCallbackRequest request) {
        if (!MessageDigest.isEqual(
                internalSecret.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ArtworkException(ArtworkErrorCode.INTERNAL_SECRET_INVALID);
        }
        artworkService.handleImageProcessedCallback(new ImageProcessedCallbackCommand(
                request.artworkId(), request.imageKey(), request.thumbKey(),
                request.thumbAdultKey(), request.originalAvifKey(), request.status()
        ));
    }
}
