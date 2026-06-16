package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ArtworkRepository extends MongoRepository<Artwork, String> {

    // 탈퇴 회원 작품 일괄 처리
    List<Artwork> findAllByAuthorId(String authorId);

    // Worker 재시도 스케줄러 — 10분 이상 PROCESSING 상태인 작품
    @Query("{ 'status': 'PROCESSING', 'updatedAt': { $lt: ?0 } }")
    List<Artwork> findStuckProcessingArtworks(Instant threshold);
}
