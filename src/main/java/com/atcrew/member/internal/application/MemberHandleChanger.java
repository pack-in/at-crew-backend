package com.atcrew.member.internal.application;

import com.atcrew.common.persistence.DuplicateKeyViolations;
import com.atcrew.member.HandleChangeInfo;
import com.atcrew.member.MemberHandleChangedEvent;
import com.atcrew.member.internal.domain.HandleValidator;
import com.atcrew.member.internal.domain.Member;
import com.atcrew.member.internal.domain.MemberHandle;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import com.atcrew.member.internal.persistence.MemberHandleRepository;
import com.atcrew.member.internal.persistence.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * handle 변경의 트랜잭션 단위 (member-handle-design §1.3).
 *
 * <p>{@link MemberServiceImpl#changeHandle}이 트랜잭션 없이 이 빈을 호출한다. 같은 회원의 동시 요청에서 진 쪽은
 * {@code @Version} 충돌로 트랜잭션이 rollback-only가 되므로, "같은 값이 이미 반영됐는가" 재판정은 그 트랜잭션이 끝난 뒤
 * {@link #findConcurrentReplay} 새 읽기 트랜잭션에서 해야 한다 — 같은 트랜잭션에서 성공을 반환하면 커밋 시점에
 * {@code UnexpectedRollbackException}이 난다.
 */
@Component
class MemberHandleChanger {

    private static final Logger log = LoggerFactory.getLogger(MemberHandleChanger.class);

    private final MemberRepository memberRepository;
    private final MemberHandleRepository memberHandleRepository;
    private final ApplicationEventPublisher eventPublisher;

    MemberHandleChanger(MemberRepository memberRepository, MemberHandleRepository memberHandleRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.memberRepository = memberRepository;
        this.memberHandleRepository = memberHandleRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    HandleChangeInfo change(String memberId, String requested) {
        Member member = findActiveMember(memberId);
        Instant now = now();
        String value = normalize(requested);

        // 오류 우선순위는 member-handle-design §1.3 순서 그대로다 — 멱등 → 30일 → 미변경 → 형식 → 원장.
        if (member.isHandleChangeReplay(value, now)) {
            return new HandleChangeInfo(member.getHandle(), member.handleChangeAvailableAt(now));
        }
        member.assertHandleChangeAllowed(now);
        if (value.isEmpty() || value.equals(member.getHandle())) {
            throw new MemberException(MemberErrorCode.HANDLE_UNCHANGED, "memberId=" + memberId);
        }
        String handle = HandleValidator.validate(value);
        Optional<MemberHandle> ledger = memberHandleRepository.findById(handle);
        if (ledger.isPresent() && !ledger.get().getMemberId().equals(memberId)) {
            // 탈퇴 회원 소유 포함 — 한 번 쓰인 handle은 영구히 그 회원 소유다.
            throw new MemberException(MemberErrorCode.DUPLICATE_HANDLE, handle);
        }

        member.changeHandle(handle, now);
        // members를 원장보다 먼저 flush한다. 같은 회원의 동시 요청은 여기서 @Version 검사로 한쪽이 실패하고,
        // 서로 다른 회원이 같은 handle을 동시에 요청하면 uk_members_handle이 한쪽을 막는다. 커밋 시점까지
        // 미루면 서비스 밖에서 ObjectOptimisticLockingFailureException이 터져 500이 된다(PortfolioServiceImpl 패턴).
        try {
            memberRepository.saveAndFlush(member);
        } catch (OptimisticLockingFailureException e) {
            throw new MemberException(MemberErrorCode.HANDLE_CHANGE_CONFLICT,
                    "memberId=" + memberId + " cause=" + e.getMessage());
        } catch (DataIntegrityViolationException e) {
            if (!DuplicateKeyViolations.isDuplicateKey(e)) {
                throw e;
            }
            throw new MemberException(MemberErrorCode.DUPLICATE_HANDLE, handle);
        }
        // 본인 소유(복귀·대소문자만 변경)면 원장 행이 이미 있어 넣지 않는다.
        if (ledger.isEmpty()) {
            try {
                memberHandleRepository.saveAndFlush(new MemberHandle(handle, memberId));
            } catch (DataIntegrityViolationException e) {
                if (!DuplicateKeyViolations.isDuplicateKey(e)) {
                    throw e;
                }
                throw new MemberException(MemberErrorCode.DUPLICATE_HANDLE, handle);
            }
        }
        log.info("handle 변경: memberId={} handle={}", memberId, handle);
        eventPublisher.publishEvent(new MemberHandleChangedEvent(memberId));
        return new HandleChangeInfo(handle, member.handleChangeAvailableAt(now));
    }

    /**
     * {@code HANDLE_CHANGE_CONFLICT}로 끝난 요청이 사실은 같은 값의 동시 재전송(더블클릭)이었는지 커밋된 상태로 다시
     * 판정한다. 판정 기준은 멱등 재전송과 같다 — 현재 handle이 요청값(strip)과 대소문자까지 같고 마지막 직접 변경이
     * 5분 이내. 맞으면 이긴 요청과 같은 성공 응답을, 아니면 빈 값을 돌려준다.
     */
    @Transactional(readOnly = true)
    Optional<HandleChangeInfo> findConcurrentReplay(String memberId, String requested) {
        Member member = findActiveMember(memberId);
        Instant now = now();
        if (!member.isHandleChangeReplay(normalize(requested), now)) {
            return Optional.empty();
        }
        return Optional.of(new HandleChangeInfo(member.getHandle(), member.handleChangeAvailableAt(now)));
    }

    // handle_changed_at은 DATETIME(6)이다. 나노초를 그대로 두면 응답의 handleChangeAvailableAt과 다시 읽은 값이
    // 어긋나 같은 변경의 재전송 응답이 첫 응답과 달라진다.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String normalize(String requested) {
        return requested == null ? "" : requested.strip();
    }

    private Member findActiveMember(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, memberId));
        if (!member.isActive()) {
            throw new MemberException(MemberErrorCode.MEMBER_DEACTIVATED, memberId);
        }
        return member;
    }
}
