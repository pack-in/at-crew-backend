package com.atcrew.search.internal.application;

import com.atcrew.recruit.RecruitIndexInfo;
import com.atcrew.search.internal.domain.RecruitSearchDocument;

import java.util.List;

/** {@code RecruitIndexInfo} → {@code RecruitSearchDocument} 변환. */
class RecruitSearchMapper {

    private RecruitSearchMapper() {
    }

    static RecruitSearchDocument toDocument(RecruitIndexInfo info) {
        return new RecruitSearchDocument(
                info.id(),
                info.postType().name(),
                info.title(),
                // ES에는 enum 상수 이름(keyword)으로 색인한다 — 필터도 같은 이름으로 매칭한다(§9-2).
                info.roles() == null ? List.of() : info.roles().stream().map(Enum::name).toList(),
                info.genres() == null ? List.of() : info.genres().stream().map(Enum::name).toList(),
                info.authorId(),
                info.authorName(),
                info.thumbnailKey(),
                info.status(),
                info.createdAt(),
                info.updatedAt()
        );
    }
}
