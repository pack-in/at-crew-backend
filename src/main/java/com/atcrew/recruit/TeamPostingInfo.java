package com.atcrew.recruit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 팀원모집글 상세 응답 (docs/design/recruit-module-design.md §2.2).
 */
public record TeamPostingInfo(
        String id,                                 // 팀원모집글 ID
        String authorMemberId,                     // 작성자 Member ID
        String authorDisplayName,                  // 작성자 계정 표시명 (member 모듈 조회, 조회 실패 시 null)
        String title,                               // 모집글 제목
        boolean isBusinessRegistered,               // 사업자등록 여부
        boolean isResumeRequired,                   // 이력서 필수 여부
        boolean isCoverLetterRequired,              // 자기소개서 필수 여부
        String authorName,                          // 모집자(팀/개인) 표시명 (폼 직접 입력)
        String contact,                             // 연락처
        String authorDescription,                   // 모집자 소개
        List<String> recruitPurposes,               // 모집 목적 (표시 전용)
        TeamWorkLocationType workLocationType,      // 활동 형태
        String activityRegion,                      // 활동 지역 (ONLINE이면 null)
        List<String> roles,                         // 모집 역할
        List<String> genres,                        // 모집 장르
        boolean hasParticipationFee,                 // 참여비용 존재 여부
        boolean hasProfitSharing,                    // 수익배분 존재 여부
        String extraCost,                            // 추가 비용 설명
        LocalDate deadline,                          // 마감일 (null이면 상시모집)
        Integer recruitCount,                        // 모집 인원
        String selectionProcess,                     // 선발 절차
        TeamActivityDuration activityDuration,       // 예상 활동 기간
        TeamWeeklyActivityTime weeklyActivityTime,   // 주당 활동 시간
        String projectDescription,                   // 프로젝트 소개
        String thumbnailImage,                       // 썸네일 이미지 URL
        List<String> referenceImages,                // 참고 이미지 URL 목록 (표시 전용)
        long bookmarkCount,                          // 북마크 수
        long viewCount,                               // 조회 수
        Instant boostedUntil,                         // 끌어올리기 만료 시각 (null이거나 과거면 상단고정 아님)
        TeamPostingStatus status,                     // 상태
        Instant deletedAt,                      // 휴지통 이동 시각 (null이면 휴지통 아님)
        Instant createdAt,                       // 생성 시각
        Instant updatedAt                        // 수정 시각
) {
}
