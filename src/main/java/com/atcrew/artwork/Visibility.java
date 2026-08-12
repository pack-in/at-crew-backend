package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        작품 공개 범위 — 업로드 시 지정하고, 이후에는 작품이 READY일 때만 공개 상태 변경 API로 바꿀 수 있다.
        - PUBLIC: 전체 공개. 커뮤니티·검색·북마크 목록에 노출된다.
        - LINK_ONLY: 링크를 아는 사람만 상세를 열람할 수 있다. 북마크 저장은 되지만 북마크 목록에는 나오지 않는다.
        - PRIVATE: 비공개. 본인만 열람할 수 있고, 라이브 포트폴리오에 포함된 경우에만 타인 열람이 허용된다.
        휴지통으로 이동하면 PRIVATE로 강제 변경되고 복구 시 삭제 직전 값으로 되돌아간다.""",
        example = "PUBLIC")
public enum Visibility {
    PUBLIC, LINK_ONLY, PRIVATE
}
