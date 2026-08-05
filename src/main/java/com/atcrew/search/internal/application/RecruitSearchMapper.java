package com.atcrew.search.internal.application;

import com.atcrew.recruit.RecruitIndexInfo;
import com.atcrew.search.internal.domain.RecruitSearchDocument;

/** {@code RecruitIndexInfo} → {@code RecruitSearchDocument} 변환. */
class RecruitSearchMapper {

    private RecruitSearchMapper() {
    }

    static RecruitSearchDocument toDocument(RecruitIndexInfo info) {
        return new RecruitSearchDocument(
                info.id(),
                info.postType().name(),
                info.title(),
                info.roles(),
                info.genres(),
                info.authorId(),
                info.authorName(),
                info.thumbnailKey(),
                info.status(),
                info.createdAt(),
                info.updatedAt()
        );
    }
}
