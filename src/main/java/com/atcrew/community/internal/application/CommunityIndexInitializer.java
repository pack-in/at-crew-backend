package com.atcrew.community.internal.application;

import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.stereotype.Component;

@Component
class CommunityIndexInitializer {

    private final MongoTemplate mongoTemplate;

    CommunityIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    @SuppressWarnings({"deprecation", "removal"})
    void ensureIndexes() {
        mongoTemplate.indexOps("banners").ensureIndex(
                new CompoundIndexDefinition(Document.parse("{'status': 1, 'sortOrder': 1}"))
                        .named("idx_banner_status_sort"));
    }
}
