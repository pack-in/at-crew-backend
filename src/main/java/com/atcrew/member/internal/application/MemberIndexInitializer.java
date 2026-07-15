package com.atcrew.member.internal.application;

import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * (loginEmail, authProvider) 복합 unique 인덱스를 애플리케이션 기동 시 생성한다.
 *
 * @CompoundIndex(sparse=true)가 아닌 partialFilterExpression을 사용하는 이유:
 * sparse는 "두 필드 모두 null일 때만" 인덱스에서 제외하는 반면,
 * partialFilter는 "loginEmail이 string 타입인 문서만" 인덱싱해 탈퇴 회원(loginEmail=null)을
 * 정확히 제외한다. 이를 통해 탈퇴 후 재가입 시 인덱스 충돌이 발생하지 않는다.
 *
 * 운영 DB 최초 배포 시 기존 sparse 인덱스를 먼저 drop해야 한다:
 * db.members.dropIndex("idx_login_email_provider")
 */
@Component
class MemberIndexInitializer {

    private final MongoTemplate mongoTemplate;

    MemberIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    @SuppressWarnings({"deprecation", "removal"})
    void ensureIndexes() {
        // partial() 반환 타입이 Index(IndexDefinition)이므로 인라인으로 직접 전달
        mongoTemplate.indexOps("members").ensureIndex(
                new CompoundIndexDefinition(Document.parse("{'loginEmail': 1, 'authProvider': 1}"))
                        .named("idx_login_email_provider")
                        .unique()
                        .partial(PartialIndexFilter.of(Criteria.where("loginEmail").type(2))));  // 2 = BSON String

        // 커뮤니티 "작가 찾아보기" — 최신 업데이트순 정렬 경로
        mongoTemplate.indexOps("members").ensureIndex(
                new CompoundIndexDefinition(Document.parse("{'active': 1, 'employmentStatus': 1, 'updatedAt': -1}"))
                        .named("idx_member_search_updated"));
        // 커뮤니티 "작가 찾아보기" — 경력순 정렬 경로
        mongoTemplate.indexOps("members").ensureIndex(
                new CompoundIndexDefinition(Document.parse(
                        "{'active': 1, 'employmentStatus': 1, 'experienceRank': -1, 'updatedAt': -1}"))
                        .named("idx_member_search_experience"));
    }
}
