package com.atcrew.recruit;

public enum JobSeekingPostStatus {
    DRAFT,      // 임시저장 (작성 중)
    PUBLISHED,  // 생성 즉시 공개 노출 (승인 절차 없음)
    CLOSED,     // 마감 (작성자 종료)
    DELETED     // 휴지통 (소프트 삭제)
}
