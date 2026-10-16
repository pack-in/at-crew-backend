package com.atcrew.common.id;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * 작품·공유 포트폴리오 6자 공개 코드 발급 (docs/design/member-handle-design.md §3).
 *
 * <p>{@code [a-z0-9]} 6자이며 작가(소유자) 안에서만 유일하면 된다. 유일성 판정은 소유 모듈이 넘기는
 * {@code isTaken}(같은 작가의 코드 존재 여부 사전 조회)으로 하고, 겹치면 최대 {@value #MAX_ATTEMPTS}번까지 뽑는다.
 * INSERT의 UNIQUE 위반을 잡아 같은 트랜잭션에서 다시 뽑는 방식은 트랜잭션이 rollback-only가 돼 쓸 수 없다 —
 * 사전 조회를 통과한 뒤의 동시 경쟁으로 UNIQUE 위반이 나면 소유 모듈이 자기 에러코드로 끝낸다.
 *
 * <p>artwork·portfolio 두 모듈이 같은 규칙을 써야 하므로 {@link UuidV7Generator}와 같은 공용 ID 패키지에 둔다.
 */
@Component
public class PublicCodeGenerator {

    public static final int LENGTH = 6;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int MAX_ATTEMPTS = 3;

    private final RandomGenerator random;

    public PublicCodeGenerator() {
        this(new SecureRandom());
    }

    /** 테스트가 난수를 고정해 충돌 경로를 결정적으로 재현하기 위한 생성자. */
    public PublicCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** 이미 쓰인 코드를 피해 새 코드를 뽑는다. {@value #MAX_ATTEMPTS}번 모두 겹치면 빈 값. */
    public Optional<String> generate(Predicate<String> isTaken) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!isTaken.test(code)) {
                return Optional.of(code);
            }
        }
        return Optional.empty();
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
