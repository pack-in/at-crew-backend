package com.atcrew.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 복제 시작 시 프론트가 생성 화면을 미리 채우는 데 쓰는 원본 정보 (docs/design/portfolio-module-design.md §5.3).
 *
 * <p>이 응답만으로는 복제본이 만들어지지 않는다 — 프론트가 이 값으로 생성 API를 다시 호출한다.
 * 원본 유형(작가 페이지/공유, 최신 반영형/고정형)은 복제본에 상속되지 않고 매번 새로 선택하기 때문이다.
 */
@Schema(description = "복제 시작 화면을 미리 채우는 원본 정보 — 이 응답만으로는 복제본이 만들어지지 않는다")
public record PortfolioDuplicationSourceInfo(
        @Schema(description = "기본 제목 — \"{원본 제목} 복사본\", 작가 페이지 원본이면 \"{사용자 이름} 복사본\". "
                + "생성 API의 title 기본값으로 쓴다", example = "라이브 공유 포트폴리오 복사본")
        String defaultTitle,             // 기본 제목 — "{원본 제목} 복사본", 작가 페이지는 "{사용자 이름} 복사본"

        @Schema(description = "자동 선택될 작품 ID — 원본 구성 순서 유지. 0개여도 그대로 복제를 진행할 수 있다. "
                + "생성 API의 artworkIds 기본값으로 쓴다",
                example = "[\"019ff382-bd4a-7045-80ac-7430bd0832c7\",\"019ff382-bd56-7e7e-830b-40e3fbd6a0d3\"]")
        List<String> selectedArtworkIds, // 자동 선택될 작품 ID (원본 구성 순서 유지, 0개여도 복제 진행 가능)

        @Schema(description = "삭제·휴지통·비공개(PRIVATE)라 자동 선택에서 빠진 작품 수 — 안내 문구 노출용. "
                + "링크공개(LINK_ONLY)는 제외 대상이 아니다", example = "0")
        int excludedCount                // 삭제·비공개라 자동 선택에서 빠진 작품 수
) {
}
