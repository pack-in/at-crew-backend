package com.atcrew.community;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        배너 상태.
        - ACTIVE: 노출 중. `GET /api/community/banners` 목록에 포함되는 유일한 상태다.
        - INACTIVE: 비노출(수동 숨김). 현재 이 상태로 바꾸는 API는 없다.
        - DELETED: 삭제(soft delete). `DELETE /api/community/banners/{bannerId}` 호출 결과이며 목록에서 빠진다.""",
        example = "ACTIVE")
public enum BannerStatus {
    ACTIVE,   // 노출 중
    INACTIVE, // 비노출 (수동 숨김)
    DELETED   // 삭제 (soft delete)
}
