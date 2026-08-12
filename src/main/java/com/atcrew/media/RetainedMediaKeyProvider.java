package com.atcrew.media;

import java.util.Collection;
import java.util.Set;

/**
 * 삭제·정리 대상 R2 key 중 아직 참조되고 있어 보존해야 하는 key를 알려주는 확장점.
 *
 * <p>media는 어떤 모듈이 왜 key를 붙잡고 있는지 알 필요가 없다 — 구현체를 빈으로 등록하면 media의
 * 정리 경로(고아 키 스케줄러)와 소비자(artwork 영구 삭제 리스너)가 그 key를 건너뛴다. 구현체가
 * 하나도 없으면(모듈 단위 테스트 부트스트랩 등) 보존 대상이 없는 것으로 취급한다.
 *
 * <p>현재 구현체는 portfolio 모듈의 고정형 스냅샷 보존 규칙 하나뿐이다
 * (docs/design/portfolio-module-design.md §5.6).
 */
public interface RetainedMediaKeyProvider {

    /**
     * 후보 key 중 보존해야 하는 key만 돌려준다. 후보에 없는 key는 반환하지 않는다.
     */
    Set<String> retainedKeys(Collection<String> candidateKeys);
}
