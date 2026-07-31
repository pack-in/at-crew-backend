package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.artwork.OrphanedImageKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrphanedImageKeyRepository extends JpaRepository<OrphanedImageKey, String> {
}
