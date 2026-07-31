package com.atcrew.recruit.internal.application;

import com.atcrew.common.response.CursorPage;
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
 * 검색어로 좋아요한 작가를 거르는 기능은 search 모듈 연동이 필요해 이번 스코프에서는 정렬만 제공한다(§2.7).
 */
@Service
@Transactional
class LikedArtistService {

    private final LikedArtistRepository likedArtistRepository;
    private final RecentlyViewedArtistRepository recentlyViewedArtistRepository;
    private final AuthorNameResolver authorNameResolver;

    LikedArtistService(LikedArtistRepository likedArtistRepository,
            RecentlyViewedArtistRepository recentlyViewedArtistRepository, AuthorNameResolver authorNameResolver) {
        this.likedArtistRepository = likedArtistRepository;
        this.recentlyViewedArtistRepository = recentlyViewedArtistRepository;
        this.authorNameResolver = authorNameResolver;
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
    CursorPage<LikedArtistInfo> getLikedArtists(String companyMemberId, String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<LikedArtist> likes;
        if (cursor == null) {
            likes = likedArtistRepository.findFirstPage(companyMemberId, pageable);
        } else {
            CompositeCursor decoded = CompositeCursor.decode(cursor);
            likes = likedArtistRepository.findNextPage(companyMemberId, decoded.sortAt(), decoded.id(), pageable);
        }
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
