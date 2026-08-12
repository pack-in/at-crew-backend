package com.atcrew.member;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = """
        내 회원 정보 (로그인·회원가입·토큰 갱신 응답에 포함).
        값이 null이거나 빈 배열인 필드는 응답 JSON에서 생략되므로(NON_EMPTY) 프론트는 항상 존재 여부를 확인해야 한다.
        로그인 이메일은 보안상 응답에 포함되지 않는다.""")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemberInfo(

        // === 식별자 ===
        @Schema(description = "회원 ID (UUIDv7 문자열)", example = "019ff381-8156-7720-8af7-3fc34574bcc6")
        String id,

        @Schema(description = "@핸들 — URL 식별자, 전체에서 고유. 가입 시 자동 생성된다",
                example = "user_a580e617")
        String handle,

        @JsonIgnore
        String loginEmail, // 로그인 이메일 (탈퇴 시 null) — @JsonIgnore로 응답·Swagger 스키마에서 모두 제외

        // === 인증 ===
        @Schema(description = "가입 경로. EMAIL — 이메일·비밀번호 가입, GOOGLE — Google(Firebase) 가입",
                example = "EMAIL", nullable = true)
        AuthProvider authProvider,

        // === 기본 프로필 ===
        @Schema(description = "사용자 이름·작가명 (최대 16자)", example = "김창작")
        String name,

        @Schema(description = """
                창작자 유형. WEBTOON — 웹툰작가, ILLUSTRATOR — 일러스트작가, WEB_NOVELIST — 웹소설작가, OTHER — 기타.
                미설정이면 응답에서 생략된다""",
                example = "WEBTOON", nullable = true)
        CreatorRole creatorRole,

        // === 구인구직 상태 [피그마: 구인구직 상태 칩 선택] ===
        @Schema(description = """
                구인구직 상태. PREPARING — 준비중(가입 직후 기본값), AVAILABLE — 신규 작업 가능,
                NEGOTIABLE — 협의 가능, CLOSED — 마감""",
                example = "PREPARING")
        EmploymentStatus employmentStatus,

        // === 활동 분야 [피그마: 활동 분야 칩 복수 선택] ===
        @ArraySchema(arraySchema = @Schema(description = """
                활동 분야 (최대 4개). ILLUSTRATION — 일러스트, WEBTOON — 웹툰,
                PUBLISHED_MANGA — 출판만화, ANIMATION — 애니메이션.
                중복 제거 후 위 나열 순서로 정렬돼 내려가며, 비어 있으면 응답에서 생략된다""",
                example = "[\"ILLUSTRATION\",\"WEBTOON\"]"))
        List<ActivityField> activityFields,

        // === 활동 경력 [피그마: 활동 경력 칩 단일 선택] ===
        @Schema(description = """
                활동 경력 연차. NEWCOMER — 신입, ONE_TO_TWO — 1-2년차, THREE_TO_FOUR — 3-4년차,
                FIVE_TO_NINE — 5-9년차, TEN_PLUS — 10년차 이상. 미설정이면 응답에서 생략된다""",
                example = "THREE_TO_FOUR", nullable = true)
        ExperienceLevel experienceLevel,

        // === 활동 지역 [피그마: 활동 지역 칩 복수 선택] ===
        @ArraySchema(arraySchema = @Schema(description = """
                활동 지역 (최대 7개). SEOUL — 서울, GYEONGGI — 경기도, DAEJEON — 대전, DAEGU — 대구,
                GWANGJU — 광주, BUSAN — 부산, OTHER — 기타.
                중복 제거 후 위 나열 순서로 정렬돼 내려가며, 비어 있으면 응답에서 생략된다""",
                example = "[\"SEOUL\",\"GYEONGGI\"]"))
        List<ActiveRegion> activeRegions,

        // === 팀 작업 경험 [피그마: 팀 작업 경험 칩 복수 선택] ===
        @ArraySchema(arraySchema = @Schema(description = """
                팀 작업 경험 (최대 4개). NONE — 없음, SHORT_TERM — 단기 협업 팀,
                DIVISION — 분업형 팀, REGULAR_DEADLINE — 정기 마감 팀.
                중복 제거 후 위 나열 순서로 정렬돼 내려가며, 비어 있으면 응답에서 생략된다""",
                example = "[\"SHORT_TERM\",\"DIVISION\"]"))
        List<TeamExperience> teamExperiences,

        // === 슬롯 [피그마: 전체 슬롯 개수 / 작업 가능 슬롯] ===
        @Schema(description = "전체 작업 슬롯 수 (1~5, 가입 시 기본값 5)", example = "5")
        int totalSlotCount,

        @Schema(description = "현재 작업 가능한 슬롯 수 (0~5, totalSlotCount 이하)", example = "5")
        int availableSlotCount,

        // === 연락처·SNS·툴 [피그마: 작가 정보 입력 섹션] ===
        @Schema(description = "연락처 (전화번호 또는 이메일). 미설정이면 응답에서 생략된다",
                example = "010-1234-5678", nullable = true)
        String contact,

        @Schema(description = "SNS 링크. 미설정이면 응답에서 생략된다",
                example = "https://x.com/testcreator", nullable = true)
        String sns,

        @Schema(description = "사용 가능한 툴. 미설정이면 응답에서 생략된다",
                example = "Clip Studio Paint, Photoshop", nullable = true)
        String tools,

        // === 경력 목록 [피그마: 작가 경력 섹션] ===
        @ArraySchema(arraySchema = @Schema(
                description = "등록한 경력 목록 (최대 50개, 등록 순). 비어 있으면 응답에서 생략된다"))
        List<CareerEntryInfo> careers,

        // === 계정 상태 ===
        @Schema(description = "계정 활성 여부. 탈퇴하면 false", example = "true")
        boolean active,

        @Schema(description = "탈퇴 처리 시각 (UTC ISO-8601). 활성 회원이면 응답에서 생략된다",
                example = "2026-08-12T01:06:18.597552Z", nullable = true)
        Instant deletedAt,

        @Schema(description = "마지막 로그인 시각 (UTC ISO-8601). 가입 직후에는 응답에서 생략된다",
                example = "2026-08-12T01:06:18.746484Z", nullable = true)
        Instant lastLoginAt,

        @Schema(description = "가입 시각 (UTC ISO-8601)", example = "2026-08-12T01:06:18.597552Z")
        Instant createdAt,

        @Schema(description = "최종 수정 시각 (UTC ISO-8601)", example = "2026-08-12T01:06:18.746484Z")
        Instant updatedAt,

        // === 시간대·국가 ===
        @Schema(description = "IANA 시간대 ID — 클라이언트가 시각을 로컬 표시할 때 사용", example = "Asia/Seoul")
        String timezone,

        @Schema(description = "거주 국가 (ISO 3166-1 alpha-2)", example = "KR")
        String countryCode
) {
}
