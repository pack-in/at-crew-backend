package com.atcrew.recruit;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;

import java.util.List;

/**
 * 구직글 작성 커맨드 (docs/design/recruit-module-design.md §2.3, §4.2).
 * 승인 절차가 없으므로 {@code publish=true}면 저장 즉시 PUBLISHED로 게시된다.
 *
 * <p><b>TODO(구직글 복사)</b>: 기획서 마이페이지_작가-R24에 따라 구직 정보의 정본은 <b>작가 프로필</b>이고
 * 구직글은 작성 시점의 프로필 값을 복사해 온다. 다만 두 모듈의 값 집합이 서로 달라 서버가 자동 복사하면
 * 값이 조용히 사라지거나 뒤바뀐다 — 아래가 현재 상태다.
 *
 * <ul>
 *   <li>희망 장르: {@code member.DesiredGenre}(29) ↔ {@code artwork.Genre}(29) — 상수 이름까지 같아 1:1 매핑 가능</li>
 *   <li>희망 담당 업무: {@code member.DesiredRole}(23) ↔ {@code artwork.ArtworkRole}(22) — 프로필의 작화·식자가
 *       recruit에 없고, recruit의 ETC가 프로필에 없다(프로필은 직접입력으로 대체)</li>
 *   <li>선호 피드백 방식: {@code member.FeedbackPreference}(구체적·자율적·직설적·부드러운·한번에·자잘하게·상관없음)
 *       ↔ {@code FeedbackStyle}(상세한·최소한의·실시간·정기적) — <b>대응값이 전혀 없다</b></li>
 *   <li>작업 스타일: {@code member.WorkPace}(완성도 중심·속도 우선·작업별 조율)
 *       ↔ {@code WorkStyle}(독립적·협업·체계적·유연) — <b>대응값이 전혀 없다</b></li>
 * </ul>
 *
 * <p>그래서 현재는 서버가 자동 복사하지 않는다. 프로필의 구직 정보는 {@code MemberProfileInfo}로 그대로
 * 노출되므로 클라이언트가 작성 폼을 미리 채우는 방식으로 처리한다(손실 없음, 실제 UI 동작과도 일치).
 * 서버 복사를 도입하려면 위 값 집합 통일이 선행되어야 한다. 구직글은 MVP 범위가 아니라 이번 배포 대상도 아니다.
 */
public record CreateJobSeekingPostCommand(
        String title,                          // 구직글 제목
        List<ArtworkRole> roles,               // 희망 역할
        List<Genre> genres,                    // 희망 장르
        String drawingStyle,                   // 작화 스타일
        FeedbackStyle preferredFeedbackStyle,  // 선호 피드백 방식
        WorkStyle workStyle,                   // 작업 스타일
        String desiredRate,                    // 희망 단가 (자유 텍스트)
        String portfolioDescription,           // 포트폴리오 소개
        List<String> referenceImages,          // 참고 이미지 URL 목록 (표시 전용)
        boolean publish                        // true면 저장 직후 PUBLISHED로 게시, false면 DRAFT 저장
) {
}
