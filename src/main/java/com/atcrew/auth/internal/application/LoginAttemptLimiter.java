package com.atcrew.auth.internal.application;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.common.logging.LogMask;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

@Service
class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);
    private static final String COLLECTION = "login_attempts";
    private static final int EMAIL_LIMIT = 5;
    private static final int IP_LIMIT = 30;
    private static final int WINDOW_SECONDS = 600; // 10분

    private final MongoTemplate mongoTemplate;

    LoginAttemptLimiter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    @SuppressWarnings("deprecation")
    void ensureIndexes() {
        mongoTemplate.indexOps(COLLECTION)
                .ensureIndex(new Index().on("firstFailedAt", Sort.Direction.ASC).expire(WINDOW_SECONDS));
    }

    // 현재 차단 상태인지 확인 — 차단 중이면 BCrypt 연산 전에 429 반환
    void checkBlocked(String email) {
        String ip = extractIp();
        Integer emailFails = getFailCount("email:" + email);
        Integer ipFails = getFailCount("ip:" + ip);

        if ((emailFails != null && emailFails >= EMAIL_LIMIT) || (ipFails != null && ipFails >= IP_LIMIT)) {
            log.warn("로그인 차단: email={} ip={}", LogMask.email(email), ip);
            throw new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS);
        }
    }

    void recordFailure(String email) {
        increment("email:" + email);
        increment("ip:" + extractIp());
    }

    void reset(String email) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is("email:" + email)), COLLECTION);
    }

    private Integer getFailCount(String key) {
        org.bson.Document doc = mongoTemplate.findById(key, org.bson.Document.class, COLLECTION);
        return doc != null ? doc.getInteger("failCount") : null;
    }

    private void increment(String key) {
        Query query = Query.query(Criteria.where("_id").is(key));
        Update update = new Update()
                .inc("failCount", 1)
                .setOnInsert("firstFailedAt", Instant.now());
        mongoTemplate.upsert(query, update, COLLECTION);
    }

    private String extractIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            HttpServletRequest request = attrs.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
