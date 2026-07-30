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
 * artwork 색인 문서. 원본은 MongoDB {@code artworks} 컬렉션이며, 이 문서는 조회 전용 색인이다
 * (docs/design/search-module-design.md §3). {@code artworkId}를 문서 ID로 그대로 사용한다.
 *
 * <p>인덱스명 {@code artworks}는 실제로는 alias다 — 물리 인덱스는 {@code artworks_v1}처럼 버전이 붙고,
 * 재색인 시 새 물리 인덱스로 alias를 원자적으로 전환한다({@code SearchIndexInitializer}, {@code ArtworkReindexService} 참고).
 */
// createIndex=false — 기본값(true)이면 Spring Data의 SimpleElasticsearchRepository가 리포지토리 빈 생성 시
// "artworks"를 실제 물리 인덱스로 자동 생성해버려, SearchIndexInitializer가 만들려는 alias("artworks")와
// 이름이 충돌한다("invalid_alias_name_exception: an index ... exists with the same name as the alias").
// 인덱스/alias 생성은 SearchIndexInitializer가 전담한다.
@Document(indexName = "artworks", createIndex = false)
public class ArtworkSearchDocument {

    // @Id만 붙이면 ES _id 메타필드로만 쓰여 매핑에서 빠지고 정렬(search_after tie-breaker)이 실패한다
    // (all shards failed) — @Field를 함께 붙여 실제 keyword 필드로도 색인한다.
    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private String authorId;

    @Field(type = FieldType.Text)
    private String authorName;

    @Field(type = FieldType.Keyword)
    private String authorHandle;

    @Field(type = FieldType.Keyword)
    private String artworkField;

    @Field(type = FieldType.Keyword)
    private String creativeType;

    @Field(type = FieldType.Keyword)
    private String ageRating;

    @Field(type = FieldType.Keyword)
    private List<String> roles;

    @Field(type = FieldType.Keyword)
    private List<String> genres;

    @Field(type = FieldType.Keyword)
    private List<String> materialTargets;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailKey;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailAdultKey;

    // epoch_millis로 고정 — search_after 커서 값 타입을 Long으로 단순화한다
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;

    protected ArtworkSearchDocument() {
    }

    public ArtworkSearchDocument(String id, String title, String description, List<String> tags,
                                  String authorId, String authorName, String authorHandle,
                                  String artworkField, String creativeType, String ageRating,
                                  List<String> roles, List<String> genres, List<String> materialTargets,
                                  String thumbnailKey, String thumbnailAdultKey,
                                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.authorId = authorId;
        this.authorName = authorName;
        this.authorHandle = authorHandle;
        this.artworkField = artworkField;
        this.creativeType = creativeType;
        this.ageRating = ageRating;
        this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
        this.genres = genres != null ? new ArrayList<>(genres) : new ArrayList<>();
        this.materialTargets = materialTargets != null ? new ArrayList<>(materialTargets) : new ArrayList<>();
        this.thumbnailKey = thumbnailKey;
        this.thumbnailAdultKey = thumbnailAdultKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getTags() { return tags; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getAuthorHandle() { return authorHandle; }
    public String getArtworkField() { return artworkField; }
    public String getCreativeType() { return creativeType; }
    public String getAgeRating() { return ageRating; }
    public List<String> getRoles() { return roles; }
    public List<String> getGenres() { return genres; }
    public List<String> getMaterialTargets() { return materialTargets; }
    public String getThumbnailKey() { return thumbnailKey; }
    public String getThumbnailAdultKey() { return thumbnailAdultKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
