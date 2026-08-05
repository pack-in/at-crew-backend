package com.atcrew.search.internal.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 구인글·팀원모집글·구직글 색인 문서. 원본은 recruit 모듈의 JobPosting/TeamPosting/JobSeekingPost이며,
 * 이 문서는 조회 전용 색인이다(docs/design/search-module-design.md §3). {@code postId}를 문서 ID로 그대로 사용한다.
 *
 * <p>인덱스명 {@code recruit_posts}는 실제로는 alias다 — {@link ArtworkSearchDocument}와 동일한 이유로
 * 물리 인덱스는 {@code recruit_posts_v1}처럼 버전이 붙고, 재색인 시 새 물리 인덱스로 alias를 원자적으로 전환한다
 * ({@code SearchIndexInitializer}, {@code RecruitReindexService} 참고).
 */
@Document(indexName = "recruit_posts", createIndex = false)
public class RecruitSearchDocument {

    // @Id만 붙이면 ES _id 메타필드로만 쓰여 매핑에서 빠지고 정렬(search_after tie-breaker)이 실패한다
    // (all shards failed) — @Field를 함께 붙여 실제 keyword 필드로도 색인한다.
    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Keyword)
    private String postType;

    @Field(type = FieldType.Text)
    private String title;

    // 작성자가 입력한 자유 문자열 그대로 색인한다 — 태그 정본화는 별도 과제(docs/design/search-module-design.md §9-2).
    @Field(type = FieldType.Keyword)
    private List<String> roles;

    @Field(type = FieldType.Keyword)
    private List<String> genres;

    @Field(type = FieldType.Keyword)
    private String authorId;

    @Field(type = FieldType.Text)
    private String authorName;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailKey;

    // 색인 대상은 항상 PUBLISHED뿐이라 값은 사실상 고정이지만, 필드 자체는 원본 상태를 그대로 남겨 둔다.
    @Field(type = FieldType.Keyword)
    private String status;

    // epoch_millis로 고정 — search_after 커서 값 타입을 Long으로 단순화한다
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;

    protected RecruitSearchDocument() {
    }

    public RecruitSearchDocument(String id, String postType, String title, List<String> roles, List<String> genres,
                                  String authorId, String authorName, String thumbnailKey, String status,
                                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.postType = postType;
        this.title = title;
        this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
        this.genres = genres != null ? new ArrayList<>(genres) : new ArrayList<>();
        this.authorId = authorId;
        this.authorName = authorName;
        this.thumbnailKey = thumbnailKey;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getPostType() { return postType; }
    public String getTitle() { return title; }
    public List<String> getRoles() { return roles; }
    public List<String> getGenres() { return genres; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getThumbnailKey() { return thumbnailKey; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
