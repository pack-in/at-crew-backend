package com.atcrew.auth.internal.persistence;

import com.atcrew.auth.internal.domain.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenValue(String tokenValue);

    void deleteAllByMemberId(String memberId);
}
