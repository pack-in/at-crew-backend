package com.atcrew.recruit.internal.application;

import com.atcrew.common.response.CursorPage;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.recruit.CreateJobSeekingPostCommand;
import com.atcrew.recruit.JobSeekingPostInfo;
import com.atcrew.recruit.JobSeekingPostStatus;
import com.atcrew.recruit.RecruitIndexInfo;
import com.atcrew.recruit.RecruitPostChangedEvent;
import com.atcrew.recruit.RecruitPostType;
import com.atcrew.recruit.UpdateJobSeekingPostCommand;
import com.atcrew.recruit.internal.domain.JobSeekingPost;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import com.atcrew.recruit.internal.persistence.JobSeekingPostRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 구직글 CRUD (docs/design/recruit-module-design.md §2.3, §4.2).
 * 모듈 공개 API는 {@link com.atcrew.recruit.RecruitService} 하나이므로 이 클래스는 그 구현이 위임하는 내부 협력자다.
 */
@Service
@Transactional
class JobSeekingPostService {

    private final JobSeekingPostRepository jobSeekingPostRepository;
    private final AuthorNameResolver authorNameResolver;
    private final RecruitImageService recruitImageService;
    private final ApplicationEventPublisher eventPublisher;

    JobSeekingPostService(JobSeekingPostRepository jobSeekingPostRepository, AuthorNameResolver authorNameResolver,
            RecruitImageService recruitImageService, ApplicationEventPublisher eventPublisher) {
        this.jobSeekingPostRepository = jobSeekingPostRepository;
        this.authorNameResolver = authorNameResolver;
        this.recruitImageService = recruitImageService;
        this.eventPublisher = eventPublisher;
    }

    JobSeekingPostInfo create(String memberId, CreateJobSeekingPostCommand command) {
        JobSeekingPost post = JobSeekingPost.create(memberId, command);
        if (command.publish()) {
            post.publish();
        }
        JobSeekingPost saved = jobSeekingPostRepository.save(post);
        // 구직글은 썸네일 없이 참고 이미지만 쓴다 — presign으로 발급받은 key를 media 모듈에 등록한다(설계 §10.3).
        RecruitImageService.apply(
                recruitImageService.register(MediaOwnerType.JOB_SEEKING_POST, saved.getId(),
                        null, command.referenceImages()),
                saved::markImageProcessingPending, saved::markImageProcessingReady);
        publishChanged(saved.getId());
        return toInfo(saved);
    }

    JobSeekingPostInfo update(String memberId, String postId, UpdateJobSeekingPostCommand command) {
        JobSeekingPost post = getOwned(postId, memberId);
        if (post.getStatus() == JobSeekingPostStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.JOB_SEEKING_POST_NOT_FOUND, postId);
        }
        post.updateContent(command);
        // 부분 업데이트라 이미지 필드를 실제로 보낸 요청만 media 재등록 대상이다(설계 §10.3).
        if (command.referenceImages() != null) {
            RecruitImageService.apply(
                    recruitImageService.replace(MediaOwnerType.JOB_SEEKING_POST, postId,
                            null, post.getReferenceImages()),
                    post::markImageProcessingPending, post::markImageProcessingReady);
        }
        publishChanged(postId);
        return toInfo(post); // 트랜잭션 커밋 시점 dirty checking — 명시적 save() 불필요
    }

    JobSeekingPostInfo publish(String memberId, String postId) {
        JobSeekingPost post = getOwned(postId, memberId);
        post.publish();
        publishChanged(postId);
        return toInfo(post);
    }

    JobSeekingPostInfo close(String memberId, String postId) {
        JobSeekingPost post = getOwned(postId, memberId);
        post.close();
        publishChanged(postId);
        return toInfo(post);
    }

    void delete(String memberId, String postId) {
        getOwned(postId, memberId).moveToTrash();
        publishChanged(postId);
    }

    @Transactional(readOnly = true)
    CursorPage<JobSeekingPostInfo> getTrashed(String memberId, String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<JobSeekingPost> posts = cursor == null
                ? jobSeekingPostRepository.findByAuthorMemberIdAndStatusOrderByIdDesc(
                        memberId, JobSeekingPostStatus.DELETED, pageable)
                : jobSeekingPostRepository.findByAuthorMemberIdAndStatusAndIdLessThanOrderByIdDesc(
                        memberId, JobSeekingPostStatus.DELETED, cursor, pageable);
        return toInfoPage(posts, size);
    }

    JobSeekingPostInfo restore(String memberId, String postId) {
        JobSeekingPost post = getOwned(postId, memberId);
        post.restore();
        publishChanged(postId);
        return toInfo(post);
    }

    @Transactional(readOnly = true)
    CursorPage<JobSeekingPostInfo> getMine(String memberId, String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<JobSeekingPost> posts = cursor == null
                ? jobSeekingPostRepository.findByAuthorMemberIdAndStatusNotOrderByIdDesc(
                        memberId, JobSeekingPostStatus.DELETED, pageable)
                : jobSeekingPostRepository.findByAuthorMemberIdAndStatusNotAndIdLessThanOrderByIdDesc(
                        memberId, JobSeekingPostStatus.DELETED, cursor, pageable);
        return toInfoPage(posts, size);
    }

    @Transactional(readOnly = true)
    JobSeekingPostInfo get(String postId, String viewerMemberId) {
        JobSeekingPost post = findById(postId);
        boolean isAuthor = viewerMemberId != null && post.getAuthorMemberId().equals(viewerMemberId);
        // 공개 노출은 PUBLISHED만 — 작성자 본인은 모든 상태 조회 가능. 그 외에는 존재 자체를 숨기기 위해 404로 통일
        if (post.getStatus() != JobSeekingPostStatus.PUBLISHED && !isAuthor) {
            throw new RecruitException(RecruitErrorCode.JOB_SEEKING_POST_NOT_FOUND, postId);
        }
        return toInfo(post);
    }

    @Transactional(readOnly = true)
    CursorPage<JobSeekingPostInfo> getPublished(String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);
        List<JobSeekingPost> posts = cursor == null
                ? jobSeekingPostRepository.findByStatusOrderByIdDesc(JobSeekingPostStatus.PUBLISHED, pageable)
                : jobSeekingPostRepository.findByStatusAndIdLessThanOrderByIdDesc(
                        JobSeekingPostStatus.PUBLISHED, cursor, pageable);
        return toInfoPage(posts, size);
    }

    @Transactional(readOnly = true)
    Optional<RecruitIndexInfo> getForIndexing(String postId) {
        return jobSeekingPostRepository.findById(postId).map(this::toIndexInfo);
    }

    @Transactional(readOnly = true)
    CursorPage<RecruitIndexInfo> getForReindex(String cursor, int size) {
        int limit = size + 1;
        List<JobSeekingPost> posts = cursor != null
                ? jobSeekingPostRepository.findByCreatedAtAfterOrderByCreatedAtAsc(parseCursor(cursor), PageRequest.of(0, limit))
                : jobSeekingPostRepository.findAllByOrderByCreatedAtAsc(PageRequest.of(0, limit));
        if (posts.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = posts.size() > size;
        List<JobSeekingPost> page = hasNext ? posts.subList(0, size) : posts;
        List<RecruitIndexInfo> items = page.stream().map(this::toIndexInfo).toList();
        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getCreatedAt().toEpochMilli()) : null;
        return CursorPage.of(items, nextCursor);
    }

    private void publishChanged(String postId) {
        eventPublisher.publishEvent(new RecruitPostChangedEvent(postId, RecruitPostType.JOB_SEEKING));
    }

    // 구직글은 썸네일이 없다(설계 §2.3) — thumbnailKey는 항상 null.
    private RecruitIndexInfo toIndexInfo(JobSeekingPost post) {
        return new RecruitIndexInfo(
                post.getId(),
                RecruitPostType.JOB_SEEKING,
                post.getTitle(),
                post.getRoles(),
                post.getGenres(),
                post.getAuthorMemberId(),
                authorNameResolver.resolve(post.getAuthorMemberId()),
                null,
                post.getStatus().name(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private Instant parseCursor(String cursor) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(cursor));
        } catch (NumberFormatException e) {
            throw new RecruitException(RecruitErrorCode.INVALID_CURSOR, cursor);
        }
    }

    private JobSeekingPost getOwned(String postId, String memberId) {
        JobSeekingPost post = findById(postId);
        post.checkAuthor(memberId);
        return post;
    }

    private JobSeekingPost findById(String postId) {
        return jobSeekingPostRepository.findById(postId)
                .orElseThrow(() -> new RecruitException(RecruitErrorCode.JOB_SEEKING_POST_NOT_FOUND, postId));
    }

    private JobSeekingPostInfo toInfo(JobSeekingPost post) {
        return JobSeekingPostMapper.toInfo(post, authorNameResolver.resolve(post.getAuthorMemberId()),
                recruitImageService.load(MediaOwnerType.JOB_SEEKING_POST, post.getId()));
    }

    private CursorPage<JobSeekingPostInfo> toInfoPage(List<JobSeekingPost> posts, int size) {
        if (posts.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = posts.size() > size;
        List<JobSeekingPost> page = hasNext ? posts.subList(0, size) : posts;
        Map<String, String> authorNames = authorNameResolver.resolveAll(
                page.stream().map(JobSeekingPost::getAuthorMemberId).toList());
        Map<String, PostingImages> images = recruitImageService.loadAll(
                MediaOwnerType.JOB_SEEKING_POST, page.stream().map(JobSeekingPost::getId).toList());
        List<JobSeekingPostInfo> items = page.stream()
                .map(p -> JobSeekingPostMapper.toInfo(p, authorNames.get(p.getAuthorMemberId()), images.get(p.getId())))
                .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return CursorPage.of(items, nextCursor);
    }
}
