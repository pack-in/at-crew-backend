package com.atcrew.common.id;

import com.atcrew.support.ScriptedRandom;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 6자 공개 코드 발급기 단위 검증 (member-handle-design §3.2, §5). */
class PublicCodeGeneratorTest {

    @Test
    void 코드는_소문자_영문과_숫자_6자다() {
        PublicCodeGenerator generator = new PublicCodeGenerator();

        List<String> codes = IntStream.range(0, 2000)
                .mapToObj(i -> generator.generate(code -> false).orElseThrow())
                .toList();

        assertThat(codes).allSatisfy(code -> assertThat(code).matches("^[a-z0-9]{6}$"));
        // 알파벳 36자를 고르게 쓰는지 — 2000개 × 6자면 모든 문자가 한 번 이상 나온다.
        assertThat(String.join("", codes).chars().distinct().count()).isEqualTo(36);
    }

    @Test
    void 이미_쓰인_코드는_건너뛰고_다시_뽑는다() {
        ScriptedRandom random = new ScriptedRandom();
        random.enqueue("aaaaaa", "bbbbbb", "zz0099");
        PublicCodeGenerator generator = new PublicCodeGenerator(random);
        List<String> tried = new ArrayList<>();

        String code = generator.generate(candidate -> {
            tried.add(candidate);
            return Set.of("aaaaaa", "bbbbbb").contains(candidate);
        }).orElseThrow();

        assertThat(code).isEqualTo("zz0099");
        assertThat(tried).containsExactly("aaaaaa", "bbbbbb", "zz0099");
    }

    @Test
    void 세_번_모두_겹치면_빈_값이다() {
        ScriptedRandom random = new ScriptedRandom();
        random.enqueue("aaaaaa", "aaaaaa", "aaaaaa", "bbbbbb");
        PublicCodeGenerator generator = new PublicCodeGenerator(random);

        assertThat(generator.generate("aaaaaa"::equals)).isEmpty();
    }
}
