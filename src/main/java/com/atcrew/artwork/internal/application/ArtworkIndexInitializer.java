package com.atcrew.artwork.internal.application;

import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.stereotype.Component;

@Component
class ArtworkIndexInitializer {

    private final MongoTemplate mongoTemplate;

    ArtworkIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    @SuppressWarnings({"deprecation", "removal"})
    void ensureIndexes() {
        var artworkOps = mongoTemplate.indexOps("artworks");
        // createdAt 포함: getMyArtworks/getTrashArtworks의 createdAt DESC 정렬을 인덱스로 커버
        artworkOps.ensureIndex(new CompoundIndexDefinition(
                Document.parse("{'authorId': 1, 'status': 1, 'createdAt': -1}"))
                .named("idx_artwork_author_status"));
        // ageRating은 항상 필터링되므로 인덱스에 포함 — 커뮤니티 피드 기본 경로(artworkField 없음) 커버
        artworkOps.ensureIndex(new CompoundIndexDefinition(
                Document.parse("{'status': 1, 'visibility': 1, 'ageRating': 1, 'createdAt': -1}"))
                .named("idx_artwork_community_feed"));
        // artworkField 필터 경로 커버 (artworkField + ageRating + createdAt 정렬)
        artworkOps.ensureIndex(new CompoundIndexDefinition(
                Document.parse("{'status': 1, 'visibility': 1, 'artworkField': 1, 'ageRating': 1, 'createdAt': -1}"))
                .named("idx_artwork_field_filter"));

        var entryOps = mongoTemplate.indexOps("bookmarkEntries");
        entryOps.ensureIndex(new CompoundIndexDefinition(
                Document.parse("{'memberId': 1, 'folderId': 1, 'savedAt': -1}"))
                .named("idx_bookmark_entry_folder"));
        entryOps.ensureIndex(new CompoundIndexDefinition(
                Document.parse("{'memberId': 1, 'artworkId': 1}"))
                .named("idx_bookmark_entry_unique")
                .unique());

        var folderOps = mongoTemplate.indexOps("bookmarkFolders");
        folderOps.ensureIndex(new CompoundIndexDefinition(
                Document.parse("{'memberId': 1, 'name': 1}"))
                .named("idx_bookmark_folder_unique")
                .unique());
        folderOps.ensureIndex(new CompoundIndexDefinition(
                Document.parse("{'memberId': 1, 'sortOrder': 1}"))
                .named("idx_bookmark_folder_sort"));
    }
}
