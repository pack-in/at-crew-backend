package com.atcrew.member.internal.application;

import com.atcrew.member.ArtistProfileViewedEvent;
import com.atcrew.member.internal.domain.ProfileView;
import com.atcrew.member.internal.persistence.MemberRepository;
import com.atcrew.member.internal.persistence.ProfileViewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;

/**
 * 프로필 열람수 집계 (기획서 마이페이지_작가-R03).
 *
 * <p>마이페이지_작가 진입을 기준으로 집계하며, 동일 조회자의 24시간 이내 반복 조회는 최초 1회만
 * 센다. 24시간이 지난 재방문은 다시 집계한다.
 *
 * <p><b>비로그인 조회는 집계하지 않는다.</b> 기획서가 "비로그인 사용자의 동일 사용자 판단 기준"을
 * 홈-R14 확인 필요 항목으로 남겨둬 중복 제외 키가 정해지지 않았기 때문이다. 키 없이 세면 새로고침만으로
 * 열람수를 부풀릴 수 있어 조회순 정렬이 곧바로 신뢰를 잃는다. 판단 기준이 정해지면 여기에 추가한다.
 *
 * <p>조회는 읽기 트랜잭션이므로 커밋 이후(AFTER_COMMIT) 별도 트랜잭션에서 처리한다.
 */
@Component
class ProfileViewCounter {

    private static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    private final MemberRepository memberRepository;
    private final ProfileViewRepository profileViewRepository;

    ProfileViewCounter(MemberRepository memberRepository, ProfileViewRepository profileViewRepository) {
        this.memberRepository = memberRepository;
        this.profileViewRepository = profileViewRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onProfileViewed(ArtistProfileViewedEvent event) {
        if (event.viewerMemberId() == null) {
            return; // 비로그인 조회 — 중복 제외 키가 없어 집계 대상에서 뺀다(위 주석 참고)
        }
        if (countable(event)) {
            memberRepository.incrementProfileViewCount(event.artistMemberId());
        }
    }

    /**
     * 이번 조회를 집계해야 하는지 판단하면서 열람 기록을 갱신한다.
     *
     * <p>"조회 후 판단"이 아니라 조건부 UPDATE → 실패 시 INSERT 순서로 처리해, 같은 조회자가 동시에
     * 두 번 요청해도 열람수가 두 번 오르지 않게 한다. INSERT가 PK 충돌로 실패했다는 건 그 사이 다른
     * 트랜잭션이 24시간 창 안의 기록을 이미 남겼다는 뜻이라 집계하지 않는다.
     */
    private boolean countable(ArtistProfileViewedEvent event) {
        Instant now = event.occurredAt();
        int refreshed = profileViewRepository.touchIfStale(
                event.artistMemberId(), event.viewerMemberId(), now, now.minus(DEDUP_WINDOW));
        if (refreshed > 0) {
            return true; // 24시간이 지난 재방문
        }
        try {
            profileViewRepository.saveAndFlush(
                    new ProfileView(event.artistMemberId(), event.viewerMemberId(), now));
            return true; // 최초 조회
        } catch (DataIntegrityViolationException e) {
            return false; // 24시간 안의 반복 조회
        }
    }
}
