package com.atcrew.portfolio.internal.web;

import com.atcrew.portfolio.internal.application.SnapshotOgBackfillService;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영용 내부 API — OG 백필 2단계(docs/operations/og-image-backfill.md). media 백필과 같은 시크릿을 써서 런북의 호출을
 * 한 가지 헤더로 맞춘다({@code MediaInternalController}와 같은 인증 패턴).
 */
@Hidden
@RestController
@RequestMapping("/internal/portfolio")
class PortfolioInternalController {

    private final SnapshotOgBackfillService snapshotOgBackfillService;
    private final String internalSecret;

    PortfolioInternalController(SnapshotOgBackfillService snapshotOgBackfillService,
                                @Value("${artwork.internal.secret}") String internalSecret) {
        this.snapshotOgBackfillService = snapshotOgBackfillService;
        this.internalSecret = internalSecret;
    }

    /** media 백필(1단계)의 콜백이 다 들어온 뒤에 부른다 — 먼저 부르면 아직 OG가 없는 행이 unresolved로 남는다(재실행 안전). */
    @PostMapping("/og/backfill-snapshots")
    public SnapshotOgBackfillService.Result backfillSnapshotOg(@RequestHeader("X-Internal-Secret") String secret,
            @RequestParam(defaultValue = "500") int limit, @RequestParam(defaultValue = "0") long afterId) {
        if (!MessageDigest.isEqual(internalSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal secret is invalid");
        }
        if (limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit은 1~1000이어야 합니다.");
        }
        return snapshotOgBackfillService.backfill(limit, afterId);
    }
}
