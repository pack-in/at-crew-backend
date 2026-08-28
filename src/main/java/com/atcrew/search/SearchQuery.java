package com.atcrew.search;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.MaterialTarget;
import com.atcrew.member.Language;

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
        List<Genre> genres,
        List<MaterialTarget> materialTargets,
        // 뷰어가 노출받기로 한 게시물 언어(로그인-R16). 비어 있으면 언어 필터 미적용(비로그인).
        // 사용자가 고르는 필터 축이 아니라 서버가 뷰어 설정에서 채우는 값이라 isEmptyCriteria에 넣지 않는다.
        List<Language> viewerLanguages,
        // 성인 콘텐츠 표시 필터(설정-R10)용. 사용자가 고르는 필터 축이 아니라 서버가 뷰어 설정에서
        // 채우는 값이라 isEmptyCriteria에 넣지 않는다. recruit 콘텐츠는 연령 게이팅 대상이 아니므로
        // artwork(portfolio) 소스에만 적용된다.
        String viewerMemberId,
        boolean viewerAdultContentVisible,
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
