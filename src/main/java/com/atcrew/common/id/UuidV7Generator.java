package com.atcrew.common.id;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * MariaDB 전환(docs/design/mariadb-migration-design.md §3.1) — 애플리케이션이 직접 발급하는 PK.
 *
 * <p>UUIDv4(완전 무작위)와 달리 상위 48비트가 밀리초 타임스탬프라 삽입이 근사 단조 증가한다.
 * InnoDB 클러스터드 인덱스에서 무작위 삽입으로 인한 페이지 분할·버퍼 풀 오염을 피하기 위함이며,
 * 엄밀한 단조성(같은 밀리초 내 순서 보장)까지는 요구하지 않는다 — 근사 정렬이면 충분하다(§3.1).
 *
 * <p>RFC 9562 layout: msb = unix_ts_ms(48) | version(4) | rand_a(12), lsb = variant(2) | rand_b(62).
 */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7Generator() {
    }

    public static String generate() {
        long timestampMillis = Instant.now().toEpochMilli() & 0xFFFFFFFFFFFFL; // 하위 48비트

        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        int randA = ((randomBytes[0] & 0x0F) << 8) | (randomBytes[1] & 0xFF); // 12비트
        long msb = (timestampMillis << 16) | (0x7L << 12) | randA;

        long randB = 0;
        for (int i = 2; i < 10; i++) {
            randB = (randB << 8) | (randomBytes[i] & 0xFFL);
        }
        long lsb = (0b10L << 62) | (randB & ((1L << 62) - 1)); // variant(10) + 하위 62비트만 사용

        return new UUID(msb, lsb).toString();
    }
}
