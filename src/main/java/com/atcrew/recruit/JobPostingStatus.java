package com.atcrew.recruit;

public enum JobPostingStatus {
    DRAFT,      // 임시저장 (작성 중)
    PENDING,    // 제출됨, 관리자 승인 대기
    PUBLISHED,  // 승인 완료, 공개 노출 중
    CLOSED,     // 마감 (작성자 종료 또는 기한 만료)
    DELETED     // 휴지통 (소프트 삭제)
}
