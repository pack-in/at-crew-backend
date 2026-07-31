package com.atcrew.common.id;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * UUIDv7(RFC 9562) 문자열을 생성하는 공통 유틸리티.
 *
 * <p>상위 48비트가 유닉스 밀리초 타임스탬프라 InnoDB 클러스터드 인덱스(PK)에 근사 단조 증가로 삽입되어
 * UUIDv4 대비 페이지 분할·버퍼 풀 오염을 줄인다(docs/design/mariadb-migration-design.md §3.1).
 * 외부 라이브러리 없이 직접 구현한다.
 *
 * <p>레이아웃(16바이트, 128비트):
 * <pre>
 * |  48비트 unix_ts_ms  | 4비트 version(0111) | 12비트 rand_a | 2비트 variant(10) | 62비트 rand_b |
 * </pre>
 */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7Generator() {
    }

    /**
     * 시간순 정렬이 가능한 UUIDv7 문자열(하이픈 포함 36자)을 생성한다.
     */
    public static String generate() {
        byte[] uuidBytes = new byte[16];
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        long timestampMillis = Instant.now().toEpochMilli();

        // 48비트 유닉스 밀리초 타임스탬프 (bytes 0-5, 빅엔디안)
        uuidBytes[0] = (byte) (timestampMillis >>> 40);
        uuidBytes[1] = (byte) (timestampMillis >>> 32);
        uuidBytes[2] = (byte) (timestampMillis >>> 24);
        uuidBytes[3] = (byte) (timestampMillis >>> 16);
        uuidBytes[4] = (byte) (timestampMillis >>> 8);
        uuidBytes[5] = (byte) timestampMillis;

        // 버전 4비트(0111) + 랜덤 12비트 (byte 6 상위 4비트 = version, 나머지 12비트 = rand_a)
        uuidBytes[6] = (byte) (0x70 | (randomBytes[0] & 0x0F));
        uuidBytes[7] = randomBytes[1];

        // variant 2비트(10) + 랜덤 62비트 (byte 8 상위 2비트 = variant, 나머지 62비트 = rand_b)
        uuidBytes[8] = (byte) (0x80 | (randomBytes[2] & 0x3F));
        System.arraycopy(randomBytes, 3, uuidBytes, 9, 7);

        return toUuid(uuidBytes).toString();
    }

    private static UUID toUuid(byte[] bytes) {
        long mostSigBits = 0L;
        for (int i = 0; i < 8; i++) {
            mostSigBits = (mostSigBits << 8) | (bytes[i] & 0xFF);
        }
        long leastSigBits = 0L;
        for (int i = 8; i < 16; i++) {
            leastSigBits = (leastSigBits << 8) | (bytes[i] & 0xFF);
        }
        return new UUID(mostSigBits, leastSigBits);
    }
}
