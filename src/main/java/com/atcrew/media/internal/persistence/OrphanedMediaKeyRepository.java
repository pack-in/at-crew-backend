package com.atcrew.media.internal.persistence;
import com.atcrew.media.internal.domain.OrphanedMediaKey;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrphanedMediaKeyRepository extends JpaRepository<OrphanedMediaKey, String> { }
