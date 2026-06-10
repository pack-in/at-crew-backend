package com.atcrew.auth.internal.persistence;

import com.atcrew.auth.internal.domain.RefreshToken;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;

class RefreshTokenCustomRepositoryImpl implements RefreshTokenCustomRepository {

    private final MongoOperations mongoOperations;

    RefreshTokenCustomRepositoryImpl(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    @Override
    public Optional<RefreshToken> findAndDeleteByTokenValue(String tokenValue) {
        Query query = Query.query(Criteria.where("tokenValue").is(tokenValue));
        return Optional.ofNullable(mongoOperations.findAndRemove(query, RefreshToken.class));
    }
}
