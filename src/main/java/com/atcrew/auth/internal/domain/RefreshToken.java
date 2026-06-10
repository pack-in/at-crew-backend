package com.atcrew.auth.internal.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    @Indexed
    private String memberId;

    @Indexed(unique = true)
    private String tokenValue;

    @Indexed(expireAfterSeconds = 0) // expiresAt 도달 시 MongoDB가 자동 삭제
    private Instant expiresAt;

    @CreatedDate
    private Instant createdAt;

    protected RefreshToken() {}

    private RefreshToken(String memberId, String tokenValue, Instant expiresAt) {
        this.memberId = memberId;
        this.tokenValue = tokenValue;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken of(String memberId, String tokenValue, Instant expiresAt) {
        return new RefreshToken(memberId, tokenValue, expiresAt);
    }

    public String getMemberId() { return memberId; }
    public String getTokenValue() { return tokenValue; }
    public Instant getExpiresAt() { return expiresAt; }
}
