package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.RecruitPostType;
import com.atcrew.recruit.RecruitSearchPage;
import com.atcrew.recruit.RecruitSearchQuery;
import com.atcrew.recruit.RecruitSearchResultInfo;
import com.atcrew.recruit.internal.domain.JobPosting;
import com.atcrew.recruit.internal.domain.JobSeekingPost;
import com.atcrew.recruit.internal.domain.TeamPosting;
import com.atcrew.recruit.internal.persistence.RecruitSearchQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * recruit 3종 통합 검색 (docs/design/recruit-module-design.md §6, docs/design/search-module-design.md §1.2).
 *
 * <p>3종을 각각 조회한 뒤 (작성 시각 내림차순, 같은 시각이면 ID 오름차순)으로 병합한다. 각 소스에서
 * {@code size + 1}건을 가져오므로 병합 결과가 요청 크기를 넘으면 다음 페이지가 남아 있다는 뜻이다.
 */
@Service
@Transactional(readOnly = true)
class RecruitSearchService {

    // 병합 정렬 키 — 다음 페이지 커서(createdAt, id)와 동일한 순서여야 keyset 페이지네이션이 성립한다.
    private static final Comparator<Candidate> ORDER = Comparator
            .comparing(Candidate::createdAt, Comparator.reverseOrder())
            .thenComparing(Candidate::id);

    private final RecruitSearchQueryRepository recruitSearchQueryRepository;
    private final AuthorNameResolver authorNameResolver;

    RecruitSearchService(RecruitSearchQueryRepository recruitSearchQueryRepository,
            AuthorNameResolver authorNameResolver) {
        this.recruitSearchQueryRepository = recruitSearchQueryRepository;
        this.authorNameResolver = authorNameResolver;
    }

    RecruitSearchPage search(RecruitSearchQuery query) {
        int limit = query.size() + 1;
        List<Candidate> candidates = new ArrayList<>();
        long totalCount = 0;

        if (query.includes(RecruitPostType.JOB_POSTING)) {
            Page<JobPosting> found = recruitSearchQueryRepository.searchJobPostings(query, limit);
            found.getContent().forEach(p -> candidates.add(new Candidate(p.getId(), RecruitPostType.JOB_POSTING,
                    p.getTitle(), p.getThumbnailImage(), p.getAuthorMemberId(), p.getCreatedAt())));
            totalCount += found.getTotalElements();
        }
        if (query.includes(RecruitPostType.TEAM_RECRUIT)) {
            Page<TeamPosting> found = recruitSearchQueryRepository.searchTeamPostings(query, limit);
            found.getContent().forEach(p -> candidates.add(new Candidate(p.getId(), RecruitPostType.TEAM_RECRUIT,
                    p.getTitle(), p.getThumbnailImage(), p.getAuthorMemberId(), p.getCreatedAt())));
            totalCount += found.getTotalElements();
        }
        if (query.includes(RecruitPostType.JOB_SEEKING)) {
            Page<JobSeekingPost> found = recruitSearchQueryRepository.searchJobSeekingPosts(query, limit);
            // 구직글에는 썸네일 필드가 없다(설계 §2.3) — 카드 썸네일은 항상 null이다.
            found.getContent().forEach(p -> candidates.add(new Candidate(p.getId(), RecruitPostType.JOB_SEEKING,
                    p.getTitle(), null, p.getAuthorMemberId(), p.getCreatedAt())));
            totalCount += found.getTotalElements();
        }

        if (candidates.isEmpty()) {
            return RecruitSearchPage.empty();
        }
        candidates.sort(ORDER);
        boolean hasNext = candidates.size() > query.size();
        List<Candidate> page = hasNext ? candidates.subList(0, query.size()) : candidates;

        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(Candidate::authorMemberId).toList());
        List<RecruitSearchResultInfo> items = page.stream()
                .map(c -> new RecruitSearchResultInfo(c.id(), c.postType(), c.title(), c.thumbnailUrl(),
                        c.authorMemberId(), authorNames.get(c.authorMemberId()), c.createdAt()))
                .toList();
        return new RecruitSearchPage(items, hasNext, totalCount);
    }

    /** 3종 조회 결과를 하나로 병합하기 위한 내부 표현 — 작성자 표시명 조회 전 단계다. */
    private record Candidate(
            String id,                // 게시글 ID
            RecruitPostType postType, // 게시글 유형
            String title,             // 제목
            String thumbnailUrl,      // 썸네일 이미지 URL (구직글은 null)
            String authorMemberId,    // 작성자 Member ID
            Instant createdAt         // 작성 일시
    ) {
    }
}
