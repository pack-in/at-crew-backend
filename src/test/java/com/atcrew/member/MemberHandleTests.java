package com.atcrew.member;

import com.atcrew.SharedContainersConfig;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사용자 아이디(handle) 해석기·변경·사전 확인·가입 통합 테스트 (docs/design/member-handle-design.md §5).
 *
 * <p>30일·5분 경계는 {@code Clock}을 주입하지 않고 handle_changed_at 픽스처를 JDBC로 직접 옮겨 검증한다.
 * 클래스 안 테스트들은 저장소를 공유하므로 handle은 매번 무작위로 만든다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class MemberHandleTests {

    private static final Duration THIRTY_DAYS = Duration.ofDays(30);

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    // ─── 해석기 (§2.1) ────────────────────────────────────────────────

    @Test
    void 현재_handle은_moved_false로_해석된다() {
        String handle = uniqueHandle("cur");
        MemberInfo member = registerDev(handle);

        ResolvedHandle resolved = memberService.resolveHandle(handle);

        assertThat(resolved.memberId()).isEqualTo(member.id());
        assertThat(resolved.currentHandle()).isEqualTo(handle);
        assertThat(resolved.moved()).isFalse();
    }

    @Test
    void 대소문자를_바꿔_조회해도_저장된_대소문자로_돌려주고_moved는_false다() {
        // 원장 PK는 _ci라 findById("ABC")가 'abc' 행을 찾으면서 엔티티 id를 요청값으로 채울 수 있다 —
        // 표시용 대소문자는 members.handle에서 가져와야 한다(PA-02 보고).
        String handle = "Mx_" + randomSuffix(6);
        MemberInfo member = registerDev(handle);

        ResolvedHandle upper = memberService.resolveHandle(handle.toUpperCase());
        ResolvedHandle lower = memberService.resolveHandle(handle.toLowerCase());

        assertThat(upper.currentHandle()).isEqualTo(handle);
        assertThat(upper.moved()).isFalse();
        assertThat(upper.memberId()).isEqualTo(member.id());
        assertThat(lower.currentHandle()).isEqualTo(handle);
        assertThat(lower.moved()).isFalse();
    }

    @Test
    void 옛_handle은_현재_handle과_moved_true로_해석된다() {
        String oldHandle = uniqueHandle("old");
        MemberInfo member = registerDev(oldHandle);
        String newHandle = "New_" + randomSuffix(6);
        memberService.changeHandle(member.id(), newHandle);

        ResolvedHandle resolved = memberService.resolveHandle(oldHandle.toUpperCase());

        assertThat(resolved.memberId()).isEqualTo(member.id());
        assertThat(resolved.currentHandle()).isEqualTo(newHandle);
        assertThat(resolved.moved()).isTrue();
        // 공개 프로필도 옛 handle로 열리고 현재 handle을 담는다
        assertThat(memberService.findProfileByHandle(oldHandle, null).handle()).isEqualTo(newHandle);
    }

    @Test
    void 탈퇴_회원의_handle은_MEMBER_WITHDRAWN() {
        String handle = uniqueHandle("wd");
        MemberInfo member = registerDev(handle);
        memberService.deactivate(member.id());

        assertErrorCode(() -> memberService.resolveHandle(handle), MemberErrorCode.MEMBER_WITHDRAWN);
        assertErrorCode(() -> memberService.findProfileByHandle(handle, null), MemberErrorCode.MEMBER_WITHDRAWN);
    }

    @Test
    void 원장에_없는_handle은_MEMBER_NOT_FOUND() {
        assertErrorCode(() -> memberService.resolveHandle(uniqueHandle("none")), MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 옛_handle로_조회하면_ArtistProfileViewedEvent를_발행하지_않는다(PublishedEvents events) {
        String oldHandle = uniqueHandle("evo");
        MemberInfo artist = registerDev(oldHandle);
        MemberInfo viewer = registerDev(uniqueHandle("evv"));
        String newHandle = uniqueHandle("evn");
        memberService.changeHandle(artist.id(), newHandle);

        memberService.findProfileByHandle(oldHandle, viewer.id());
        assertThat(events.ofType(ArtistProfileViewedEvent.class)
                .matching(e -> artist.id().equals(e.artistMemberId()))).isEmpty();

        // 대조군 — 현재 handle(대소문자만 다른 요청 포함)로는 발행한다
        memberService.findProfileByHandle(newHandle.toUpperCase(), viewer.id());
        assertThat(events.ofType(ArtistProfileViewedEvent.class)
                .matching(e -> artist.id().equals(e.artistMemberId()))).hasSize(1);
    }

    // ─── 변경 (§1.3) ──────────────────────────────────────────────────

    @Test
    void 가입_handle은_30일_제한에_산정되지_않아_가입_직후_변경할_수_있다(PublishedEvents events) {
        MemberInfo member = registerDev(uniqueHandle("fresh"));
        assertThat(member.handleChangeAvailableAt()).isNull();

        String newHandle = "Fresh_" + randomSuffix(5);
        HandleChangeInfo result = memberService.changeHandle(member.id(), "  " + newHandle + "　");

        assertThat(result.handle()).isEqualTo(newHandle);
        Instant changedAt = changedAtOf(member.id());
        assertThat(result.handleChangeAvailableAt()).isEqualTo(changedAt.plus(THIRTY_DAYS));
        MemberInfo after = memberService.findById(member.id());
        assertThat(after.handle()).isEqualTo(newHandle);
        assertThat(after.handleChangeAvailableAt()).isEqualTo(changedAt.plus(THIRTY_DAYS));
        assertThat(events.ofType(MemberHandleChangedEvent.class)
                .matching(e -> member.id().equals(e.memberId()))).hasSize(1);
    }

    @Test
    void 마지막_변경으로부터_30일에서_1초_모자라면_HANDLE_CHANGE_TOO_SOON() {
        MemberInfo member = registerDev(uniqueHandle("b30a"));
        setChangedAt(member.id(), Instant.now().minus(THIRTY_DAYS).plusSeconds(1));

        assertErrorCode(() -> memberService.changeHandle(member.id(), uniqueHandle("b30x")),
                MemberErrorCode.HANDLE_CHANGE_TOO_SOON);
        assertThat(memberService.findById(member.id()).handleChangeAvailableAt()).isNotNull();
    }

    @Test
    void 마지막_변경으로부터_30일하고_1초가_지나면_변경할_수_있다() {
        MemberInfo member = registerDev(uniqueHandle("b30b"));
        setChangedAt(member.id(), Instant.now().minus(THIRTY_DAYS).minusSeconds(1));
        assertThat(memberService.findById(member.id()).handleChangeAvailableAt()).isNull();

        String newHandle = uniqueHandle("b30y");
        assertThat(memberService.changeHandle(member.id(), newHandle).handle()).isEqualTo(newHandle);
    }

    @Test
    void 본인의_옛_handle로_복귀하면_원장에_새로_넣지_않는다() {
        String original = uniqueHandle("back");
        MemberInfo member = registerDev(original);
        memberService.changeHandle(member.id(), uniqueHandle("away"));
        setChangedAt(member.id(), Instant.now().minus(THIRTY_DAYS).minusSeconds(1));

        HandleChangeInfo result = memberService.changeHandle(member.id(), original);

        assertThat(result.handle()).isEqualTo(original);
        assertThat(ledgerCountOf(member.id())).isEqualTo(2);
        assertThat(memberService.resolveHandle(original).moved()).isFalse();
    }

    @Test
    void 대소문자만_바꿔도_변경_1회로_산정되고_원장_행은_그대로다() {
        String handle = "case_" + randomSuffix(6);
        MemberInfo member = registerDev(handle);
        String recased = "Case_" + handle.substring(5).toUpperCase();

        HandleChangeInfo result = memberService.changeHandle(member.id(), recased);

        assertThat(result.handle()).isEqualTo(recased);
        assertThat(ledgerCountOf(member.id())).isEqualTo(1);
        assertThat(memberService.findById(member.id()).handle()).isEqualTo(recased);
        assertErrorCode(() -> memberService.changeHandle(member.id(), uniqueHandle("case2")),
                MemberErrorCode.HANDLE_CHANGE_TOO_SOON);
    }

    @Test
    void 다른_회원의_현재_옛_handle은_DUPLICATE_HANDLE이고_실패는_변경_이력을_남기지_않는다() {
        String othersOld = uniqueHandle("oth");
        MemberInfo other = registerDev(othersOld);
        String othersCurrent = uniqueHandle("othn");
        memberService.changeHandle(other.id(), othersCurrent);
        MemberInfo member = registerDev(uniqueHandle("me"));

        assertErrorCode(() -> memberService.changeHandle(member.id(), othersCurrent.toUpperCase()),
                MemberErrorCode.DUPLICATE_HANDLE);
        assertErrorCode(() -> memberService.changeHandle(member.id(), othersOld),
                MemberErrorCode.DUPLICATE_HANDLE);
        // 실패는 30일 제한을 시작하지 않는다(R17)
        assertThat(changedAtOf(member.id())).isNull();
    }

    @Test
    void 탈퇴_회원이_쓰던_handle은_DUPLICATE_HANDLE() {
        String withdrawn = uniqueHandle("gone");
        MemberInfo leaver = registerDev(withdrawn);
        memberService.deactivate(leaver.id());
        MemberInfo member = registerDev(uniqueHandle("taker"));

        assertErrorCode(() -> memberService.changeHandle(member.id(), withdrawn), MemberErrorCode.DUPLICATE_HANDLE);
    }

    @Test
    void 같은_값을_5분_안에_다시_보내면_성공으로_응답하고_이벤트는_한_번만_발행한다(PublishedEvents events) {
        MemberInfo member = registerDev(uniqueHandle("idem"));
        String newHandle = uniqueHandle("idemn");
        HandleChangeInfo first = memberService.changeHandle(member.id(), newHandle);

        HandleChangeInfo replay = memberService.changeHandle(member.id(), newHandle);

        assertThat(replay).isEqualTo(first);
        assertThat(events.ofType(MemberHandleChangedEvent.class)
                .matching(e -> member.id().equals(e.memberId()))).hasSize(1);
    }

    @Test
    void 같은_값이어도_5분이_지나면_30일_제한_오류다() {
        MemberInfo member = registerDev(uniqueHandle("idm5"));
        String newHandle = uniqueHandle("idm5n");
        memberService.changeHandle(member.id(), newHandle);
        setChangedAt(member.id(), Instant.now().minus(Duration.ofMinutes(5)).minusSeconds(1));

        assertErrorCode(() -> memberService.changeHandle(member.id(), newHandle),
                MemberErrorCode.HANDLE_CHANGE_TOO_SOON);
    }

    @Test
    void 대소문자만_다른_재전송은_멱등이_아니라_30일_제한_오류다() {
        MemberInfo member = registerDev(uniqueHandle("idmc"));
        String newHandle = "Idmc_" + randomSuffix(6);
        memberService.changeHandle(member.id(), newHandle);

        assertErrorCode(() -> memberService.changeHandle(member.id(), newHandle.toLowerCase()),
                MemberErrorCode.HANDLE_CHANGE_TOO_SOON);
    }

    @Test
    void 빈_값과_현재와_같은_값은_HANDLE_UNCHANGED() {
        String handle = uniqueHandle("same");
        MemberInfo member = registerDev(handle);

        assertErrorCode(() -> memberService.changeHandle(member.id(), "   "), MemberErrorCode.HANDLE_UNCHANGED);
        assertErrorCode(() -> memberService.changeHandle(member.id(), null), MemberErrorCode.HANDLE_UNCHANGED);
        assertErrorCode(() -> memberService.changeHandle(member.id(), handle), MemberErrorCode.HANDLE_UNCHANGED);
    }

    @Test
    void 형식_오류는_검증기_코드로_거부하되_30일_제한이_먼저다() {
        MemberInfo member = registerDev(uniqueHandle("fmt"));
        assertErrorCode(() -> memberService.changeHandle(member.id(), "ab"), MemberErrorCode.HANDLE_TOO_SHORT);
        assertErrorCode(() -> memberService.changeHandle(member.id(), "Search"), MemberErrorCode.HANDLE_RESERVED);

        setChangedAt(member.id(), Instant.now().minus(Duration.ofDays(1)));
        assertErrorCode(() -> memberService.changeHandle(member.id(), "ab"), MemberErrorCode.HANDLE_CHANGE_TOO_SOON);
    }

    @Test
    void 탈퇴_회원은_변경할_수_없다() {
        MemberInfo member = registerDev(uniqueHandle("dead"));
        memberService.deactivate(member.id());

        assertErrorCode(() -> memberService.changeHandle(member.id(), uniqueHandle("dead2")),
                MemberErrorCode.MEMBER_DEACTIVATED);
    }

    // ─── 동시성 (§1.3, §5) ────────────────────────────────────────────

    @Test
    void 서로_다른_두_회원이_같은_handle을_동시에_요청하면_한_명만_성공한다() throws Exception {
        MemberInfo first = registerDev(uniqueHandle("ra"));
        MemberInfo second = registerDev(uniqueHandle("rb"));
        String contested = uniqueHandle("race");

        MemberException loserError = runWhileFirstHoldsLock(
                () -> memberService.changeHandle(first.id(), contested),
                () -> memberService.changeHandle(second.id(), contested));

        assertThat(loserError.getCode()).isEqualTo(MemberErrorCode.DUPLICATE_HANDLE.name());
        assertThat(memberService.resolveHandle(contested).memberId()).isEqualTo(first.id());
        assertThat(changedAtOf(second.id())).isNull();
    }

    @Test
    void 같은_회원의_동시_변경은_한쪽만_성공하고_나머지는_HANDLE_CHANGE_CONFLICT다() throws Exception {
        MemberInfo member = registerDev(uniqueHandle("dbl"));
        String winner = uniqueHandle("dblw");
        String loser = uniqueHandle("dbll");

        MemberException loserError = runWhileFirstHoldsLock(
                () -> memberService.changeHandle(member.id(), winner),
                () -> memberService.changeHandle(member.id(), loser));

        assertThat(loserError.getCode()).isEqualTo(MemberErrorCode.HANDLE_CHANGE_CONFLICT.name());
        assertThat(memberService.findById(member.id()).handle()).isEqualTo(winner);
        // 실패한 쪽의 원장 insert도 같은 트랜잭션이라 롤백된다
        assertErrorCode(() -> memberService.resolveHandle(loser), MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 같은_회원이_같은_값을_동시에_두_번_보내면_둘_다_성공하고_변경은_한_번만_기록된다() throws Exception {
        // 더블클릭 — 진 쪽은 @Version 충돌이지만, 이긴 쪽이 같은 값을 이미 반영했으므로 멱등 재전송과 같이 성공이다.
        MemberInfo member = registerDev(uniqueHandle("twin"));
        String requested = "Twin_" + randomSuffix(6);
        long versionBefore = versionOf(member.id());
        HandleChangeInfo[] firstResult = new HandleChangeInfo[1];

        HandleChangeInfo secondResult = secondResultWhileFirstHoldsLock(
                () -> firstResult[0] = memberService.changeHandle(member.id(), requested),
                () -> memberService.changeHandle(member.id(), " " + requested + " "));

        assertThat(firstResult[0].handle()).isEqualTo(requested);
        assertThat(secondResult).isEqualTo(firstResult[0]);
        // 변경 1회: members 갱신 1번, 원장 행은 가입분 + 새 handle 1행
        assertThat(versionOf(member.id())).isEqualTo(versionBefore + 1);
        assertThat(ledgerCountOf(member.id())).isEqualTo(2);
        assertThat(changedAtOf(member.id()).plus(THIRTY_DAYS)).isEqualTo(secondResult.handleChangeAvailableAt());
    }

    // ─── 사전 확인 (§1.4) ─────────────────────────────────────────────

    @Test
    void 비로그인_사전_확인은_형식_오류와_점유_여부를_오류_코드로_답한다() {
        String taken = uniqueHandle("tkn");
        registerDev(taken);
        String withdrawn = uniqueHandle("tknw");
        memberService.deactivate(registerDev(withdrawn).id());

        assertThat(memberService.checkHandleAvailability(uniqueHandle("free"), null))
                .isEqualTo(new HandleAvailabilityInfo(true, null));
        assertThat(memberService.checkHandleAvailability("ab", null))
                .isEqualTo(new HandleAvailabilityInfo(false, "HANDLE_TOO_SHORT"));
        assertThat(memberService.checkHandleAvailability(null, null))
                .isEqualTo(new HandleAvailabilityInfo(false, "HANDLE_REQUIRED"));
        assertThat(memberService.checkHandleAvailability(taken.toUpperCase(), null))
                .isEqualTo(new HandleAvailabilityInfo(false, "DUPLICATE_HANDLE"));
        // 탈퇴 회원 소유도 활성 회원 소유와 같은 응답 — 탈퇴 여부를 드러내지 않는다
        assertThat(memberService.checkHandleAvailability(withdrawn, null))
                .isEqualTo(new HandleAvailabilityInfo(false, "DUPLICATE_HANDLE"));
    }

    @Test
    void 로그인_사전_확인은_본인이_소유한_현재_옛_handle을_사용_가능으로_본다() {
        String mineOld = uniqueHandle("mine");
        MemberInfo member = registerDev(mineOld);
        String mineCurrent = uniqueHandle("minen");
        memberService.changeHandle(member.id(), mineCurrent);
        String others = uniqueHandle("othr");
        registerDev(others);

        assertThat(memberService.checkHandleAvailability(mineOld, member.id()).available()).isTrue();
        assertThat(memberService.checkHandleAvailability(mineCurrent.toUpperCase(), member.id()).available()).isTrue();
        assertThat(memberService.checkHandleAvailability(others, member.id()))
                .isEqualTo(new HandleAvailabilityInfo(false, "DUPLICATE_HANDLE"));
    }

    @Test
    void 로그인_사전_확인에서_현재_handle과_대소문자까지_같으면_HANDLE_UNCHANGED다() {
        // 변경 API(§1.3 4단계)와 같은 판정 — 사전 확인이 "사용 가능"이라 하고 변경이 HANDLE_UNCHANGED로 거부하지 않게 한다.
        String old = uniqueHandle("unch");
        MemberInfo member = registerDev(old);
        String current = "Unch_" + randomSuffix(6);
        memberService.changeHandle(member.id(), current);

        assertThat(memberService.checkHandleAvailability(current, member.id()))
                .isEqualTo(new HandleAvailabilityInfo(false, "HANDLE_UNCHANGED"));
        assertThat(memberService.checkHandleAvailability(" " + current + " ", member.id()))
                .isEqualTo(new HandleAvailabilityInfo(false, "HANDLE_UNCHANGED"));
        // 대소문자만 다른 현재 handle·옛 handle은 변경 가능하므로 사용 가능
        assertThat(memberService.checkHandleAvailability(current.toLowerCase(), member.id()))
                .isEqualTo(new HandleAvailabilityInfo(true, null));
        assertThat(memberService.checkHandleAvailability(old, member.id()))
                .isEqualTo(new HandleAvailabilityInfo(true, null));
        // 비로그인이면 본인 판정이 없으므로 점유 중(DUPLICATE_HANDLE)이다
        assertThat(memberService.checkHandleAvailability(current, null))
                .isEqualTo(new HandleAvailabilityInfo(false, "DUPLICATE_HANDLE"));
    }

    // ─── 가입 (§4.1) ──────────────────────────────────────────────────

    @Test
    void 가입_시_입력한_handle로_가입하고_원장에_남는다() {
        String handle = "Join_" + randomSuffix(6);
        MemberInfo member = memberService.register(command(uniqueEmail(), "가입자", AuthProvider.EMAIL, handle));

        assertThat(member.handle()).isEqualTo(handle);
        assertThat(member.handleChangeAvailableAt()).isNull();
        assertThat(changedAtOf(member.id())).isNull();
        assertThat(memberService.resolveHandle(handle).memberId()).isEqualTo(member.id());
    }

    @Test
    void Google_가입도_입력한_handle을_쓴다() {
        String handle = "Ggl_" + randomSuffix(6);
        MemberInfo member = memberService.register(command(uniqueEmail(), "구글가입", AuthProvider.GOOGLE, handle));

        assertThat(member.authProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(memberService.resolveHandle(handle).memberId()).isEqualTo(member.id());
    }

    @Test
    void 가입_handle이_null이거나_빈_값이거나_공백뿐이면_HANDLE_REQUIRED로_거부하고_회원과_원장을_만들지_않는다() {
        int membersBefore = memberCount();
        int ledgerBefore = ledgerCount();

        for (AuthProvider provider : List.of(AuthProvider.EMAIL, AuthProvider.GOOGLE)) {
            for (String blank : Arrays.asList(null, "", "   ", "\u3000\t")) {
                assertErrorCode(() -> memberService.register(command(uniqueEmail(), "빈값", provider, blank)),
                        MemberErrorCode.HANDLE_REQUIRED);
            }
        }

        assertThat(memberCount()).isEqualTo(membersBefore);
        assertThat(ledgerCount()).isEqualTo(ledgerBefore);
    }

    @Test
    void 가입_handle_형식_오류는_검증기_코드로_거부한다() {
        assertErrorCode(() -> memberService.register(command(uniqueEmail(), "형식", AuthProvider.EMAIL, "a__b_c")),
                MemberErrorCode.HANDLE_CONSECUTIVE_UNDERSCORES);
        assertErrorCode(() -> memberService.register(command(uniqueEmail(), "형식", AuthProvider.EMAIL, " ab ")),
                MemberErrorCode.HANDLE_TOO_SHORT);
    }

    @Test
    void 이메일_중복과_handle_중복을_구분한다() {
        String email = uniqueEmail();
        String handle = uniqueHandle("dupj");
        memberService.register(command(email, "선가입", AuthProvider.EMAIL, handle));

        // 이메일도 handle도 겹치면 이메일이 먼저다
        assertErrorCode(() -> memberService.register(command(email, "후가입", AuthProvider.EMAIL, handle)),
                MemberErrorCode.DUPLICATE_EMAIL);
        assertErrorCode(() -> memberService.register(
                        command(uniqueEmail(), "후가입", AuthProvider.EMAIL, handle.toUpperCase())),
                MemberErrorCode.DUPLICATE_HANDLE);
    }

    @Test
    void 탈퇴_회원이_쓰던_handle로는_다시_가입할_수_없다() {
        String handle = uniqueHandle("rejoin");
        MemberInfo leaver = memberService.register(command(uniqueEmail(), "탈퇴자", AuthProvider.EMAIL, handle));
        memberService.deactivate(leaver.id());

        assertErrorCode(() -> memberService.register(command(uniqueEmail(), "재가입", AuthProvider.EMAIL, handle)),
                MemberErrorCode.DUPLICATE_HANDLE);
        // 개발용 가입 경로도 원장을 본다
        assertErrorCode(() -> memberService.register(uniqueEmail(), handle, "개발가입"),
                MemberErrorCode.DUPLICATE_HANDLE);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────

    /**
     * 첫 작업을 열린 트랜잭션 안에서 실행해 행 잠금을 쥔 채로, 두 번째 작업이 같은 행(또는 같은 unique 키)을
     * UPDATE하려고 대기하는 것을 PROCESSLIST로 확인한 뒤 커밋한다. 두 번째 작업은 커밋 전에 스냅샷을 읽었으므로
     * 반드시 충돌 경로로 끝난다 — sleep에 기대지 않는 결정적 재현이다. 두 번째 작업의 MemberException을 돌려준다.
     */
    private MemberException runWhileFirstHoldsLock(Runnable first, Runnable second) throws Exception {
        try {
            secondResultWhileFirstHoldsLock(first, () -> {
                second.run();
                return null;
            });
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(MemberException.class);
            return (MemberException) e.getCause();
        }
        throw new AssertionError("두 번째 작업이 성공했다");
    }

    /** {@link #runWhileFirstHoldsLock}과 같은 순서로 실행하고 두 번째 작업의 결과를 돌려준다(실패는 ExecutionException). */
    private <T> T secondResultWhileFirstHoldsLock(Runnable first, Callable<T> second) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            List<Future<T>> secondFuture = new ArrayList<>();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                first.run();
                secondFuture.add(pool.submit(second));
                awaitBlockedMemberUpdate();
            });
            return secondFuture.getFirst().get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private void awaitBlockedMemberUpdate() {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            Long waiting = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.PROCESSLIST"
                            + " WHERE COMMAND = 'Query' AND LOWER(INFO) LIKE 'update members %'", Long.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("두 번째 요청의 members UPDATE 대기를 확인하지 못했다");
    }

    private MemberInfo registerDev(String handle) {
        return memberService.register(uniqueEmail(), handle, "핸들회원");
    }

    private RegisterMemberCommand command(String email, String name, AuthProvider provider, String handle) {
        return new RegisterMemberCommand(email, name, provider, provider == AuthProvider.EMAIL ? "Secure1!" : null,
                true, true, true, false, "Asia/Seoul", "KR", Language.KO, handle);
    }

    private void setChangedAt(String memberId, Instant changedAt) {
        jdbcTemplate.update("UPDATE members SET handle_changed_at = ? WHERE id = ?",
                Timestamp.from(changedAt), memberId);
    }

    private Instant changedAtOf(String memberId) {
        Timestamp value = jdbcTemplate.queryForObject(
                "SELECT handle_changed_at FROM members WHERE id = ?", Timestamp.class, memberId);
        return value == null ? null : value.toInstant();
    }

    private long versionOf(String memberId) {
        Long version = jdbcTemplate.queryForObject("SELECT version FROM members WHERE id = ?", Long.class, memberId);
        return version == null ? 0 : version;
    }

    private int memberCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Integer.class);
        return count == null ? 0 : count;
    }

    private int ledgerCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_handles", Integer.class);
        return count == null ? 0 : count;
    }

    private int ledgerCountOf(String memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_handles WHERE member_id = ?", Integer.class, memberId);
        return count == null ? 0 : count;
    }

    // 접두사 ≤5자 + 무작위 6자 = 최대 12자 — 새 규칙(4~14자, 영문·숫자·밑줄)을 통과한다
    private static String uniqueHandle(String prefix) {
        return prefix + "_" + randomSuffix(6);
    }

    private static String randomSuffix(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }

    private static String uniqueEmail() {
        return "handle-" + UUID.randomUUID() + "@atcrew.com";
    }

    private static void assertErrorCode(Runnable action, MemberErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(expected.name());
    }
}
