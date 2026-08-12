package com.atcrew.portfolio;

import java.util.List;

/**
 * 복제 시작 시 프론트가 생성 화면을 미리 채우는 데 쓰는 원본 정보 (docs/design/portfolio-module-design.md §5.3).
 *
 * <p>이 응답만으로는 복제본이 만들어지지 않는다 — 프론트가 이 값으로 생성 API를 다시 호출한다.
 * 원본 유형(작가 페이지/공유, 최신 반영형/고정형)은 복제본에 상속되지 않고 매번 새로 선택하기 때문이다.
 */
public record PortfolioDuplicationSourceInfo(
        String defaultTitle,             // 기본 제목 — "{원본 제목} 복사본", 작가 페이지는 "{사용자 이름} 복사본"
        List<String> selectedArtworkIds, // 자동 선택될 작품 ID (원본 구성 순서 유지, 0개여도 복제 진행 가능)
        int excludedCount                // 삭제·비공개라 자동 선택에서 빠진 작품 수
) {
}
