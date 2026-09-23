package com.atcrew.media.internal.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 회원당 presign 발급 횟수를 제한한다(#216).
 *
 * <p>발급 자체는 DB를 쓰지 않지만, 발급받은 URL로 R2에 올린 파일은 작품·게시글에 등록되기 전까지 어디에도
 * 기록되지 않는다. 등록하지 않으면 고아 큐에도 들어가지 않아(고아 큐는 "등록됐다가 빠진 key"만 받는다)
 * 아무도 모르는 원본이 쌓인다. 인증만 통과하면 무제한이었다.
 *
 * <p>창(window) 안의 발급 <b>개수</b>를 센다 — 요청 수가 아니라 key 수다. 한 번에 30장을 받는 것과 한 장씩
 * 30번 받는 것이 같은 무게여야 한다.
 *
 * <p>기록은 인스턴스 메모리에만 둔다. 앱을 2대로 늘리면(ha-expansion-path.md) 인스턴스마다 따로 세므로
 * 실효 한도가 대수만큼 커진다 — 그때는 공유 저장소로 옮겨야 한다. 지금은 1대 구성이라 이 단순함을 택한다.
 */
@Component
public class PresignRateLimiter {

    /** 이 수를 넘으면 창이 지난 회원을 맵에서 지운다. 요청마다 전부 훑지 않으려는 값이다. */
    private static final int EVICT_THRESHOLD = 1_000;

    private final int limit;
    private final Duration window;
    /** 회원 ID → 창 안에서 발급한 시각들. 오래된 것부터 버린다. */
    private final Map<String, Deque<Instant>> issued = new HashMap<>();

    PresignRateLimiter(@Value("${media.presign.limit-per-window:300}") int limit,
                       @Value("${media.presign.window:PT1H}") Duration window) {
        if (limit < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("presign 발급 한도 설정이 유효하지 않다: limit=" + limit + " window=" + window);
        }
        this.limit = limit;
        this.window = window;
    }

    /** 이번 요청({@code count}장)을 허용할 수 있으면 기록하고 true. 한도를 넘으면 아무것도 기록하지 않고 false. */
    public synchronized boolean tryIssue(String memberId, int count) {
        Instant now = Instant.now();
        Deque<Instant> times = issued.computeIfAbsent(memberId, key -> new ArrayDeque<>());
        Instant cutoff = now.minus(window);
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.pollFirst();
        }
        boolean allowed = times.size() + count <= limit;
        if (allowed) {
            for (int i = 0; i < count; i++) {
                times.addLast(now);
            }
        }
        if (times.isEmpty()) {
            issued.remove(memberId);   // 창이 빈 회원을 맵에 남겨두지 않는다
        }
        evictStale(cutoff);
        return allowed;
    }

    /**
     * 창이 지난 회원을 맵에서 지운다. 발급이 뜸한 회원이 계속 쌓이면 맵이 회원 수만큼 커진다 — 요청마다 전부
     * 훑지 않도록 맵이 일정 크기를 넘을 때만 돈다.
     */
    private void evictStale(Instant cutoff) {
        if (issued.size() < EVICT_THRESHOLD) {
            return;
        }
        issued.values().forEach(times -> {
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.pollFirst();
            }
        });
        issued.values().removeIf(Deque::isEmpty);
    }
}
