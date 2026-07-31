package com.atcrew.recruit;

import java.time.LocalDate;
import java.util.List;

/**
 * 팀원모집글 수정 커맨드 (docs/design/recruit-module-design.md §4.2). 부분 업데이트 — null 필드는 기존 값을 유지한다.
 * boolean 필드는 null 허용을 위해 wrapper 타입({@link Boolean})을 사용한다.
 */
public record UpdateTeamPostingCommand(
        String title,                             // 모집글 제목
        Boolean isBusinessRegistered,             // 사업자등록 여부
        Boolean isResumeRequired,                 // 이력서 필수 여부
        Boolean isCoverLetterRequired,            // 자기소개서 필수 여부
        String authorName,                        // 모집자(팀/개인) 표시명 (폼 직접 입력)
        String contact,                           // 연락처
        String authorDescription,                 // 모집자 소개
        List<String> recruitPurposes,             // 모집 목적 (표시 전용)
        TeamWorkLocationType workLocationType,    // 활동 형태 (OFFLINE/ONLINE/HYBRID)
        String activityRegion,                    // 활동 지역 — workLocationType=ONLINE이면 반드시 null
        List<String> roles,                       // 모집 역할
        List<String> genres,                      // 모집 장르
        Boolean hasParticipationFee,              // 참여비용 존재 여부
        Boolean hasProfitSharing,                 // 수익배분 존재 여부
        String extraCost,                         // 추가 비용 설명
        LocalDate deadline,                       // 마감일 (null이면 상시모집)
        Integer recruitCount,                     // 모집 인원
        String selectionProcess,                  // 선발 절차
        TeamActivityDuration activityDuration,    // 예상 활동 기간
        TeamWeeklyActivityTime weeklyActivityTime, // 주당 활동 시간
        String projectDescription,                // 프로젝트 소개
        String thumbnailImage,                    // 썸네일 이미지 URL
        List<String> referenceImages              // 참고 이미지 URL 목록 (표시 전용)
) {
}
