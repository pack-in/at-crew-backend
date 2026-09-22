package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.internal.domain.view.ArtworkViewerType;
import com.atcrew.artwork.internal.persistence.ArtworkViewDedupRepository;
import com.atcrew.artwork.internal.persistence.ArtworkViewEventRepository;
import com.atcrew.member.MemberDeactivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 회원의 작품 열람 기록 비식별화 — 개인정보처리방침(at-crew.com/privacy)의 "탈퇴 후 삭제 또는 비식별화".
 *
 * <p>열람 이벤트는 행을 남기고 viewer_key만 지운다 — 기간 조회수·누적 조회수는 이미 집계된 사실이라
 * 탈퇴로 바뀌면 안 된다. dedup 행은 판정 외에 쓸모가 없어 지운다.
 *
 * <p>{@link ArtworkEventListener#onMemberDeactivated}와 같이 탈퇴 트랜잭션 안에서 동기로 처리한다 — 비식별화가
 * 실패하면 탈퇴도 롤백돼, 탈퇴는 됐는데 열람 기록에 회원 ID가 남는 상태가 생기지 않는다.
 */
@Component
class ArtworkViewMemberEventListener {

    private static final Logger log = LoggerFactory.getLogger(ArtworkViewMemberEventListener.class);

    private final ArtworkViewEventRepository eventRepository;
    private final ArtworkViewDedupRepository dedupRepository;

    ArtworkViewMemberEventListener(ArtworkViewEventRepository eventRepository,
                                   ArtworkViewDedupRepository dedupRepository) {
        this.eventRepository = eventRepository;
        this.dedupRepository = dedupRepository;
    }

    @EventListener
    void onMemberDeactivated(MemberDeactivatedEvent event) {
        int events = eventRepository.anonymizeViewer(ArtworkViewerType.MEMBER, event.memberId());
        int dedups = dedupRepository.deleteByViewer(ArtworkViewerType.MEMBER, event.memberId());
        log.info("탈퇴 회원 작품 열람 기록 비식별화: memberId={} events={} dedupRows={}",
                event.memberId(), events, dedups);
    }
}
