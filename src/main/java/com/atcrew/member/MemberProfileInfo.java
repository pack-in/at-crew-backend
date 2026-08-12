package com.atcrew.member;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = """
        공개 회원 프로필 (핸들 조회 응답). MemberInfo와 달리 값이 없는 필드도 null로 그대로 내려간다.
        계정 상태·이메일·시간대 등 비공개 정보는 포함하지 않는다.""")
public record MemberProfileInfo(

        // === 식별자 ===
        @Schema(description = "회원 ID (UUIDv7 문자열)", example = "019ff381-8156-7720-8af7-3fc34574bcc6")
        String id,

        @Schema(description = "@핸들 — URL 식별자, 전체에서 고유", example = "user_a580e617")
        String handle,

        // === 기본 프로필 ===
        @Schema(description = "사용자 이름·작가명 (최대 16자)", example = "김창작")
        String name,

        @Schema(description = """
                창작자 유형. WEBTOON — 웹툰작가, ILLUSTRATOR — 일러스트작가,
                WEB_NOVELIST — 웹소설작가, OTHER — 기타""",
                example = "WEBTOON", nullable = true)
        CreatorRole creatorRole,

        // === 구인구직 상태 ===
        @Schema(description = """
                구인구직 상태. PREPARING — 준비중, AVAILABLE — 신규 작업 가능,
                NEGOTIABLE — 협의 가능, CLOSED — 마감""",
                example = "AVAILABLE")
        EmploymentStatus employmentStatus,

        // === 활동 분야 ===
        @ArraySchema(arraySchema = @Schema(description = """
                활동 분야 (최대 4개). ILLUSTRATION — 일러스트, WEBTOON — 웹툰,
                PUBLISHED_MANGA — 출판만화, ANIMATION — 애니메이션.
                중복 제거 후 위 나열 순서로 정렬돼 내려가며, 미설정이면 빈 배열""",
                example = "[\"ILLUSTRATION\",\"WEBTOON\"]"))
        List<ActivityField> activityFields,

        // === 활동 경력 ===
        @Schema(description = """
                활동 경력 연차. NEWCOMER — 신입, ONE_TO_TWO — 1-2년차, THREE_TO_FOUR — 3-4년차,
                FIVE_TO_NINE — 5-9년차, TEN_PLUS — 10년차 이상""",
                example = "THREE_TO_FOUR", nullable = true)
        ExperienceLevel experienceLevel,

        // === 활동 지역 ===
        @ArraySchema(arraySchema = @Schema(description = """
                활동 지역 (최대 7개). SEOUL — 서울, GYEONGGI — 경기도, DAEJEON — 대전, DAEGU — 대구,
                GWANGJU — 광주, BUSAN — 부산, OTHER — 기타.
                중복 제거 후 위 나열 순서로 정렬돼 내려가며, 미설정이면 빈 배열""",
                example = "[\"SEOUL\",\"GYEONGGI\"]"))
        List<ActiveRegion> activeRegions,

        // === 팀 작업 경험 ===
        @ArraySchema(arraySchema = @Schema(description = """
                팀 작업 경험 (최대 4개). NONE — 없음, SHORT_TERM — 단기 협업 팀,
                DIVISION — 분업형 팀, REGULAR_DEADLINE — 정기 마감 팀.
                중복 제거 후 위 나열 순서로 정렬돼 내려가며, 미설정이면 빈 배열""",
                example = "[\"SHORT_TERM\",\"DIVISION\"]"))
        List<TeamExperience> teamExperiences,

        // === 슬롯 ===
        @Schema(description = "전체 작업 슬롯 수 (1~5, 기본값 5)", example = "3")
        int totalSlotCount,

        @Schema(description = "현재 작업 가능한 슬롯 수 (0~5, totalSlotCount 이하)", example = "2")
        int availableSlotCount,

        // === 연락처·SNS·툴 ===
        @Schema(description = "연락처 (전화번호 또는 이메일). 미설정이면 null",
                example = "010-1234-5678", nullable = true)
        String contact,

        @Schema(description = "SNS 링크. 미설정이면 null",
                example = "https://x.com/testcreator", nullable = true)
        String sns,

        @Schema(description = "사용 가능한 툴. 미설정이면 null",
                example = "Clip Studio Paint, Photoshop", nullable = true)
        String tools,

        // === 경력 목록 ===
        @ArraySchema(arraySchema = @Schema(
                description = "등록한 경력 목록 (최대 50개, 등록 순). 미등록이면 빈 배열"))
        List<CareerEntryInfo> careers,

        @Schema(description = "가입 시각 (UTC ISO-8601)", example = "2026-08-12T01:06:18.597552Z")
        Instant createdAt,

        @Schema(description = "최종 수정 시각 (UTC ISO-8601)", example = "2026-08-12T01:06:19.036390Z")
        Instant updatedAt
) {
}
