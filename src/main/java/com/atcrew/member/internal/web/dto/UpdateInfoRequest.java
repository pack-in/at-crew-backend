package com.atcrew.member.internal.web.dto;

import com.atcrew.member.ActiveRegion;
import com.atcrew.member.ActivityField;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.TeamExperience;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = """
        프로필 정보 부분 수정 요청. 모든 필드가 선택이며 생략하거나 null로 보내면 기존 값이 유지된다.
        리스트 필드는 빈 배열([])을 보내면 전체 삭제, 문자열 필드는 빈 문자열("")을 보내면 값이 비워진다.
        성공 시 본문 없이 204를 반환하므로 최신 값이 필요하면 GET /api/members/{handle}로 다시 조회한다.""")
public record UpdateInfoRequest(
        @Schema(description = """
                창작자 유형. WEBTOON — 웹툰작가, ILLUSTRATOR — 일러스트작가, WEB_NOVELIST — 웹소설작가,
                OTHER — 기타. null이면 변경 없음""",
                example = "WEBTOON", nullable = true)
        CreatorRole creatorRole,

        @Schema(description = """
                구인구직 상태. PREPARING — 준비중, AVAILABLE — 신규 작업 가능, NEGOTIABLE — 협의 가능,
                CLOSED — 마감. null이면 변경 없음""",
                example = "AVAILABLE", nullable = true)
        EmploymentStatus employmentStatus,

        @ArraySchema(arraySchema = @Schema(description = """
                활동 분야 (최대 4개). ILLUSTRATION — 일러스트, WEBTOON — 웹툰, PUBLISHED_MANGA — 출판만화,
                ANIMATION — 애니메이션. null이면 변경 없음, []이면 전체 삭제""",
                example = "[\"ILLUSTRATION\",\"WEBTOON\"]", nullable = true))
        @Size(max = 4)
        List<@NotNull ActivityField> activityFields,

        @Schema(description = """
                경력 연차. NEWCOMER — 신입, ONE_TO_TWO — 1-2년차, THREE_TO_FOUR — 3-4년차,
                FIVE_TO_NINE — 5-9년차, TEN_PLUS — 10년차 이상. null이면 변경 없음""",
                example = "THREE_TO_FOUR", nullable = true)
        ExperienceLevel experienceLevel,

        @ArraySchema(arraySchema = @Schema(description = """
                활동 지역 (최대 7개). SEOUL — 서울, GYEONGGI — 경기도, DAEJEON — 대전, DAEGU — 대구,
                GWANGJU — 광주, BUSAN — 부산, OTHER — 기타. null이면 변경 없음, []이면 전체 삭제""",
                example = "[\"SEOUL\",\"GYEONGGI\"]", nullable = true))
        @Size(max = 7)
        List<@NotNull ActiveRegion> activeRegions,

        @Schema(description = """
                전체 슬롯 수 (1~5). 이 값만 낮춰 보내면 기존 가용 슬롯 수가 이 값으로 함께 낮춰진다.
                null이면 변경 없음""",
                example = "3", nullable = true)
        @Min(1) @Max(5)
        Integer totalSlotCount,

        @Schema(description = """
                가용 슬롯 수 (0~5). 이 값을 직접 보냈는데 반영 후 전체 슬롯 수보다 크면 400 INVALID_SLOT_COUNT.
                null이면 변경 없음""",
                example = "2", nullable = true)
        @Min(0) @Max(5)
        Integer availableSlotCount,

        @ArraySchema(arraySchema = @Schema(description = """
                팀 작업 경험 (최대 4개). NONE — 없음, SHORT_TERM — 단기 협업 팀, DIVISION — 분업형 팀,
                REGULAR_DEADLINE — 정기 마감 팀. null이면 변경 없음, []이면 전체 삭제""",
                example = "[\"SHORT_TERM\",\"DIVISION\"]", nullable = true))
        @Size(max = 4)
        List<@NotNull TeamExperience> teamExperiences,

        // 전화번호 또는 이메일을 단일 필드로 통합 수신
        @Schema(description = """
                연락처 — 전화번호(010-0000-0000) 또는 이메일 형식만 허용 (최대 100자).
                null이면 변경 없음, 빈 문자열이면 값 비우기""",
                example = "010-1234-5678", nullable = true)
        @Size(max = 100)
        @Pattern(
                regexp = "^$|^(01[016789]-\\d{3,4}-\\d{4}|[\\w.%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,})$",
                message = "전화번호(010-0000-0000) 또는 이메일 형식으로 입력해주세요"
        )
        String contact,

        @Schema(description = "SNS 링크 (최대 200자). null이면 변경 없음, 빈 문자열이면 값 비우기",
                example = "https://x.com/testcreator", nullable = true)
        @Size(max = 200)
        String sns,

        @Schema(description = "사용 가능한 툴 (최대 200자). null이면 변경 없음, 빈 문자열이면 값 비우기",
                example = "Clip Studio Paint, Photoshop", nullable = true)
        @Size(max = 200)
        String tools,

        @Size(max = 64, message = "시간대 값이 올바르지 않습니다")
        @Schema(description = """
                IANA 시간대 ID, 예: "Asia/Tokyo". 목록에 없는 값이면 400 INVALID_TIMEZONE.
                null이면 변경 없음""",
                example = "Asia/Seoul", nullable = true)
        String timezone,

        @Pattern(regexp = "^[A-Z]{2}$", message = "국가 코드는 ISO 3166-1 alpha-2 형식이어야 합니다")
        @Schema(description = """
                거주 국가 (ISO 3166-1 alpha-2 대문자). 실존하지 않는 코드면 400 INVALID_COUNTRY.
                null이면 변경 없음""",
                example = "KR", nullable = true)
        String countryCode
) {
}
