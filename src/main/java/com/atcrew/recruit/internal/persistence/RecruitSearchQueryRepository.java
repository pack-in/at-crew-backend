package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.JobSeekingPostStatus;
import com.atcrew.recruit.RecruitSearchQuery;
import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.internal.domain.JobPosting;
import com.atcrew.recruit.internal.domain.JobSeekingPost;
import com.atcrew.recruit.internal.domain.TeamPosting;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 검색어 + 태그 다축 필터 + (createdAt, id) keyset 커서 조합 조회 (docs/design/recruit-module-design.md §6).
 * 3종 게시글의 검색 대상 필드명(status·title·roles·genres·createdAt·id)이 같아 하나의 조건 빌더를 공유한다.
 *
 * <p>제목은 부분 일치(LIKE)로 매칭한다 — 색인이 타지 않으므로 공개 글이 크게 늘어나면 search 모듈의
 * Elasticsearch 색인으로 옮기는 것을 검토한다(docs/design/search-module-design.md §8).
 */
@Component
public class RecruitSearchQueryRepository {

    // LIKE 패턴 특수문자 이스케이프 문자 — 검색어에 포함된 %/_ 가 와일드카드로 해석되지 않게 한다.
    private static final char LIKE_ESCAPE = '\\';

    private final JobPostingRepository jobPostingRepository;
    private final TeamPostingRepository teamPostingRepository;
    private final JobSeekingPostRepository jobSeekingPostRepository;

    public RecruitSearchQueryRepository(JobPostingRepository jobPostingRepository,
            TeamPostingRepository teamPostingRepository, JobSeekingPostRepository jobSeekingPostRepository) {
        this.jobPostingRepository = jobPostingRepository;
        this.teamPostingRepository = teamPostingRepository;
        this.jobSeekingPostRepository = jobSeekingPostRepository;
    }

    /** 조회 결과와 전체 건수를 한 번에 얻기 위해 Page로 반환한다(검색 결과 수 표시용). */
    public Page<JobPosting> searchJobPostings(RecruitSearchQuery query, int limit) {
        return jobPostingRepository.findAll(
                specification(JobPosting.class, JobPostingStatus.PUBLISHED, query), pageable(limit));
    }

    public Page<TeamPosting> searchTeamPostings(RecruitSearchQuery query, int limit) {
        return teamPostingRepository.findAll(
                specification(TeamPosting.class, TeamPostingStatus.PUBLISHED, query), pageable(limit));
    }

    public Page<JobSeekingPost> searchJobSeekingPosts(RecruitSearchQuery query, int limit) {
        return jobSeekingPostRepository.findAll(
                specification(JobSeekingPost.class, JobSeekingPostStatus.PUBLISHED, query), pageable(limit));
    }

    // 다중 소스 병합을 위해 3종 모두 (createdAt DESC, id ASC) 동일 정렬을 사용한다.
    private Pageable pageable(int limit) {
        return PageRequest.of(0, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    private <T> Specification<T> specification(Class<T> entityType, Enum<?> publishedStatus, RecruitSearchQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), publishedStatus));
            addTitlePredicate(predicates, builder, root, query.q());
            addTagPredicate(predicates, builder, criteriaQuery, root, entityType, "roles", query.roles());
            addTagPredicate(predicates, builder, criteriaQuery, root, entityType, "genres", query.genres());
            addCursorPredicate(predicates, builder, root, query);
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private <T> void addTitlePredicate(List<Predicate> predicates, CriteriaBuilder builder, Root<T> root, String q) {
        if (q == null || q.isBlank()) {
            return;
        }
        // 검색어 소문자 변환은 로케일에 좌우되지 않도록 ROOT 기준으로 맞춘다(DB의 LOWER와 짝을 이룬다).
        predicates.add(builder.like(
                builder.lower(root.get("title")), "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%", LIKE_ESCAPE));
    }

    /**
     * 태그(ElementCollection) 필터 — 조인 대신 EXISTS 서브쿼리를 써서 다중 태그 매칭 시 행이 중복되지 않게 한다.
     */
    private <T> void addTagPredicate(List<Predicate> predicates, CriteriaBuilder builder, CriteriaQuery<?> criteriaQuery,
            Root<T> root, Class<T> entityType, String attribute, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Subquery<String> subquery = criteriaQuery.subquery(String.class);
        Root<T> subRoot = subquery.from(entityType);
        Join<T, String> tagJoin = subRoot.join(attribute);
        subquery.select(subRoot.get("id"))
                .where(builder.and(builder.equal(subRoot.get("id"), root.get("id")), tagJoin.in(values)));
        predicates.add(builder.exists(subquery));
    }

    // (createdAt, id) keyset — 정렬이 createdAt DESC, id ASC이므로 같은 시각에서는 커서보다 큰 ID가 다음 항목이다.
    private <T> void addCursorPredicate(List<Predicate> predicates, CriteriaBuilder builder, Root<T> root,
            RecruitSearchQuery query) {
        if (query.cursorCreatedAt() == null || query.cursorId() == null) {
            return;
        }
        predicates.add(builder.or(
                builder.lessThan(root.get("createdAt"), query.cursorCreatedAt()),
                builder.and(
                        builder.equal(root.get("createdAt"), query.cursorCreatedAt()),
                        builder.greaterThan(root.get("id"), query.cursorId()))));
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
