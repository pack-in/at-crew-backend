package com.atcrew.search;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;

import java.util.List;

/**
 * 검색 조건. 각 필터 축은 다중선택이며, 축 내부는 OR, 축 간에는 AND로 결합한다
 * (docs/design/search-module-design.md §1.5 — 피그마 "모든 chip은 복수선택 가능" 규칙).
 */
public record SearchQuery(
        String q,
        List<PostType> postTypes,
        List<ArtworkField> artworkFields,
        List<CreativeType> creativeTypes,
        List<AgeRating> ageRatings,
        List<ArtworkRole> roles,
        // TODO: 장르/소재 대상 정본 목록은 Notion(피그마 UI개편_검색 필터 패널 주석 참고)에 있음 — 피그마가 기획
        // 기준이므로 정본이 확정되면 enum화 검토. 현재는 자유 문자열 keyword로 색인해 목록이 바뀌어도
        // 재색인만으로 흡수된다(docs/design/search-module-design.md §1.4).
        List<String> genres,
        List<String> materialTargets,
        SearchSort sort,
        String cursor,
        int size
) {

    /** 검색어와 모든 필터가 비어 있는지 여부 — 최초 진입 상태(§1.5)를 판별한다. */
    public boolean isEmptyCriteria() {
        return isBlank(q)
                && isEmpty(postTypes) && isEmpty(artworkFields) && isEmpty(creativeTypes)
                && isEmpty(ageRatings) && isEmpty(roles) && isEmpty(genres) && isEmpty(materialTargets);
    }

    /** postTypes가 지정되지 않았거나 PORTFOLIO를 포함하는지 여부. */
    public boolean includesPortfolio() {
        return isEmpty(postTypes) || postTypes.contains(PostType.PORTFOLIO);
    }

    /** postTypes가 recruit 소유 유형(JOB_POSTING/JOB_SEEKING/TEAM_RECRUIT) 중 하나라도 포함하는지 여부. */
    public boolean includesRecruitOwned() {
        return isEmpty(postTypes) || postTypes.stream().anyMatch(t -> t != PostType.PORTFOLIO);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
