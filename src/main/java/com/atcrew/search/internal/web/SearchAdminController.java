package com.atcrew.search.internal.web;

import com.atcrew.search.internal.application.ArtworkReindexService;
import com.atcrew.search.internal.exception.SearchErrorCode;
import com.atcrew.search.internal.exception.SearchException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 검색 인덱스 전체 재색인 트리거 — artwork의 내부 콜백 인증 패턴({@code ArtworkInternalController})과 동일. */
@Hidden
@RestController
@RequestMapping("/internal/search")
class SearchAdminController {

    private final ArtworkReindexService reindexService;
    private final String internalSecret;

    SearchAdminController(ArtworkReindexService reindexService,
                           @Value("${search.internal.secret}") String internalSecret) {
        this.reindexService = reindexService;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/reindex")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reindex(@RequestHeader("X-Internal-Secret") String secret) {
        if (!MessageDigest.isEqual(
                internalSecret.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8))) {
            throw new SearchException(SearchErrorCode.INTERNAL_SECRET_INVALID);
        }
        reindexService.reindexAll();
    }
}
