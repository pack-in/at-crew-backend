/**
 * MongoDB 초기화 스크립트 — prod 환경에서 auto-index-creation: false이므로 수동 실행 필요
 *
 * 실행 방법:
 *   mongosh $MONGODB_URI/$MONGODB_DATABASE scripts/init-db.js
 */

// refresh_tokens: expiresAt 기반 TTL 인덱스 (만료 토큰 자동 삭제)
db.refresh_tokens.createIndex(
    { expiresAt: 1 },
    { expireAfterSeconds: 0, name: "idx_refresh_tokens_expiresAt_ttl" }
);

// members: unique sparse 인덱스 (탈퇴 후 null 필드는 중복 허용)
db.members.createIndex(
    { loginEmail: 1 },
    { unique: true, sparse: true, name: "idx_members_loginEmail_unique_sparse" }
);
db.members.createIndex(
    { handle: 1 },
    { unique: true, sparse: true, name: "idx_members_handle_unique_sparse" }
);

print("인덱스 생성 완료");
