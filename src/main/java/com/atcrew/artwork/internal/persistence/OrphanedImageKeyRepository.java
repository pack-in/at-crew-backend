package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.artwork.OrphanedImageKey;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrphanedImageKeyRepository extends MongoRepository<OrphanedImageKey, String> {
}
