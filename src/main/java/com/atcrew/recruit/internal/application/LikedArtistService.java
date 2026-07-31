package com.atcrew.recruit.internal.application;

import com.atcrew.common.response.CursorPage;
import com.atcrew.member.MemberService;
import com.atcrew.recruit.LikedArtistInfo;
import com.atcrew.recruit.RecentlyViewedArtistInfo;
import com.atcrew.recruit.internal.domain.CompanyArtistId;
import com.atcrew.recruit.internal.domain.LikedArtist;
import com.atcrew.recruit.internal.domain.RecentlyViewedArtist;
import com.atcrew.recruit.internal.persistence.LikedArtistRepository;
import com.atcrew.recruit.internal.persistence.RecentlyViewedArtistRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 관심 작가 — 좋아요 저장/해제, 최근 본 작가 기록 (docs/design/recruit-module-design.md §2.7, §4.3).
 *
 * <p>기업 계정 전용 기능이지만 기업 인증 게이팅은 아직 스텁이므로(§7) 인증된 회원이면 호출할 수 있다.
 *
 * <p>좋아요한 작가 검색은 "기업이 좋아요한 작가"로 범위를 좁힌 뒤 이름·핸들 매칭만 member 모듈 공개 API에
 * 위임한다(§2.7 — recruit은 작가 검색 인덱스를 따로 두지 않는다).
 */
@Service
@Transactional
class LikedArtistService {

    private final LikedArtistRepository likedArtistRepository;
    private final RecentlyViewedArtistRepository recentlyViewedArtistRepository;
    private final AuthorNameResolver authorNameResolver;
    private final MemberService memberService;

    LikedArtistService(LikedArtistRepository likedArtistRepository,
            RecentlyViewedArtistRepository recentlyViewedArtistRepository, AuthorNameResolver authorNameResolver,
            MemberService memberService) {
        this.likedArtistRepository = likedArtistRepository;
        this.recentlyViewedArtistRepository = recentlyViewedArtistRepository;
        this.authorNameResolver = authorNameResolver;
        this.memberService = memberService;
    }

    // 좋아요 저장 — 이미 저장돼 있으면 최초 저장 시각을 유지한다(멱등, 원자적 upsert).
    void likeArtist(String companyMemberId, String artistMemberId) {
        likedArtistRepository.upsert(companyMemberId, artistMemberId, Instant.now());
    }

    // 좋아요 해제 — 저장돼 있지 않아도 성공으로 간주한다(토글 API 멱등성).
    void unlikeArtist(String companyMemberId, String artistMemberId) {
        likedArtistRepository.deleteById(new CompanyArtistId(companyMemberId, artistMemberId));
    }

    @Transactional(readOnly = true)
    CursorPage<LikedArtistInfo> getLikedArtists(String companyMemberId, String q, String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<LikedArtist> likes = (q == null || q.isBlank())
                ? findPage(companyMemberId, cursor, pageable)
                : findMatchedPage(companyMemberId, q, cursor, pageable);
        if (likes.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = likes.size() > size;
        List<LikedArtist> page = hasNext ? likes.subList(0, size) : likes;
        Map<String, String> artistNames = authorNameResolver.resolveAll(
                page.stream().map(LikedArtist::getArtistMemberId).toList());
        List<LikedArtistInfo> items = page.stream()
                .map(l -> new LikedArtistInfo(
                        l.getArtistMemberId(), artistNames.get(l.getArtistMemberId()), l.getLikedAt()))
                .toList();
        LikedArtist last = page.get(page.size() - 1);
        String nextCursor = hasNext ? CompositeCursor.encode(last.getLikedAt(), last.getArtistMemberId()) : null;
        return CursorPage.of(items, nextCursor);
    }

    private List<LikedArtist> findPage(String companyMemberId, String cursor, Pageable pageable) {
        if (cursor == null) {
            return likedArtistRepository.findFirstPage(companyMemberId, pageable);
        }
        CompositeCursor decoded = CompositeCursor.decode(cursor);
        return likedArtistRepository.findNextPage(companyMemberId, decoded.sortAt(), decoded.id(), pageable);
    }

    /**
     * 검색어 필터 — 좋아요한 작가 ID를 먼저 모아 member 모듈에 이름·핸들 매칭을 위임하고, 걸러진 ID 집합
     * 안에서 기존과 동일한 (likedAt, artistMemberId) keyset 페이지네이션을 수행한다.
     * 후보 집합은 "한 기업이 좋아요한 작가"로 한정돼 있어 ID 목록을 통째로 넘겨도 규모가 제한된다.
     */
    private List<LikedArtist> findMatchedPage(String companyMemberId, String q, String cursor, Pageable pageable) {
        List<String> likedArtistIds = likedArtistRepository.findArtistMemberIds(companyMemberId);
        if (likedArtistIds.isEmpty()) {
            return List.of();
        }
        List<String> matchedIds = memberService.findIdsByKeyword(likedArtistIds, q);
        if (matchedIds.isEmpty()) {
            return List.of();
        }
        if (cursor == null) {
            return likedArtistRepository.findFirstPageIn(companyMemberId, matchedIds, pageable);
        }
        CompositeCursor decoded = CompositeCursor.decode(cursor);
        return likedArtistRepository.findNextPageIn(
                companyMemberId, matchedIds, decoded.sortAt(), decoded.id(), pageable);
    }

    // 작가 마이페이지 조회 기록 — 같은 작가를 다시 보면 조회 시각만 갱신한다(원자적 upsert).
    void recordArtistView(String companyMemberId, String artistMemberId) {
        recentlyViewedArtistRepository.upsert(companyMemberId, artistMemberId, Instant.now());
    }

    @Transactional(readOnly = true)
    CursorPage<RecentlyViewedArtistInfo> getRecentlyViewedArtists(String companyMemberId, String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<RecentlyViewedArtist> views;
        if (cursor == null) {
            views = recentlyViewedArtistRepository.findFirstPage(companyMemberId, pageable);
        } else {
            CompositeCursor decoded = CompositeCursor.decode(cursor);
            views = recentlyViewedArtistRepository.findNextPage(
                    companyMemberId, decoded.sortAt(), decoded.id(), pageable);
        }
        if (views.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = views.size() > size;
        List<RecentlyViewedArtist> page = hasNext ? views.subList(0, size) : views;
        Map<String, String> artistNames = authorNameResolver.resolveAll(
                page.stream().map(RecentlyViewedArtist::getArtistMemberId).toList());
        List<RecentlyViewedArtistInfo> items = page.stream()
                .map(v -> new RecentlyViewedArtistInfo(
                        v.getArtistMemberId(), artistNames.get(v.getArtistMemberId()), v.getViewedAt()))
                .toList();
        RecentlyViewedArtist last = page.get(page.size() - 1);
        String nextCursor = hasNext ? CompositeCursor.encode(last.getViewedAt(), last.getArtistMemberId()) : null;
        return CursorPage.of(items, nextCursor);
    }
}
