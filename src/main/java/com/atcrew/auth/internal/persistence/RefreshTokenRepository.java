package com.atcrew.auth.internal.persistence;

import com.atcrew.auth.internal.domain.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String>, RefreshTokenCustomRepository {

    void deleteAllByMemberId(String memberId);
}
