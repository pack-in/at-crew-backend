package com.atcrew.recruit;

/**
 * 지원 커맨드 (docs/design/recruit-module-design.md §2.4).
 * 구인글 지원과 팀원모집글 지원의 요구 필드가 동일하므로 커맨드는 하나를 공유한다.
 */
public record CreateApplicationCommand(
        SerialExperience serialExperience, // 연재 경험
        boolean assistantExperience,       // 어시스턴트 경험 여부
        String resumeUrl                   // 이력서 URL (선택)
) {
}
