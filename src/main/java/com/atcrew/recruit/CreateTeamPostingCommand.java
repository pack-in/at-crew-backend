package com.atcrew.recruit;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;

import java.time.LocalDate;
import java.util.List;

/**
 * 팀원모집글 작성 커맨드 (docs/design/recruit-module-design.md §2.2, §4.2).
 * 승인 절차가 없으므로 저장 즉시 PUBLISHED로 게시된다 — JobPosting의 {@code submit} 필드에 해당하는 것이 없다.
 */
public record CreateTeamPostingCommand(
        String title,                             // 모집글 제목
        boolean isBusinessRegistered,             // 사업자등록 여부
        boolean isResumeRequired,                 // 이력서 필수 여부
        boolean isCoverLetterRequired,            // 자기소개서 필수 여부
        String authorName,                        // 모집자(팀/개인) 표시명 (폼 직접 입력)
        String contact,                           // 연락처
        String authorDescription,                 // 모집자 소개
        List<String> recruitPurposes,             // 모집 목적 (표시 전용)
        TeamWorkLocationType workLocationType,    // 활동 형태 (OFFLINE/ONLINE/HYBRID)
        String activityRegion,                    // 활동 지역 — workLocationType=ONLINE이면 반드시 null
        List<ArtworkRole> roles,                  // 모집 역할
        List<Genre> genres,                       // 모집 장르
        boolean hasParticipationFee,              // 참여비용 존재 여부
        boolean hasProfitSharing,                 // 수익배분 존재 여부
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
