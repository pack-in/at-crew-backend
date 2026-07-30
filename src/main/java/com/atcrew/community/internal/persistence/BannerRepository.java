package com.atcrew.community.internal.persistence;

import com.atcrew.community.BannerStatus;
import com.atcrew.community.internal.domain.Banner;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BannerRepository extends MongoRepository<Banner, String> {

    List<Banner> findByStatusOrderBySortOrderAsc(BannerStatus status);

    Optional<Banner> findFirstByOrderBySortOrderDesc();
}
