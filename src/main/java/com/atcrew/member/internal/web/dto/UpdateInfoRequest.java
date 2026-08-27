package com.atcrew.member.internal.web.dto;

import com.atcrew.member.ActiveRegion;
import com.atcrew.member.AvailableStartPeriod;
import com.atcrew.member.CustomTagInfo;
import com.atcrew.member.DesiredAnnualSalary;
import com.atcrew.member.DesiredEmploymentType;
import com.atcrew.member.DesiredGenre;
import com.atcrew.member.DesiredMinimumGuarantee;
import com.atcrew.member.DesiredRole;
import com.atcrew.member.DesiredWorkLocation;
import com.atcrew.member.DrawingStyle;
import com.atcrew.member.FeedbackPreference;
import com.atcrew.member.WorkPace;
import com.atcrew.member.ActivityField;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.TeamExperience;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateInfoRequest(
        @Schema(description = "구인구직 상태. null이면 변경 없음")
        EmploymentStatus employmentStatus,

        @Schema(description = "활동 분야 (최대 4개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 4)
        List<@NotNull ActivityField> activityFields,

        @Schema(description = "경력 연차. null이면 변경 없음")
        ExperienceLevel experienceLevel,

        @Schema(description = "활동 지역 (단일 선택). null이면 변경 없음")
        ActiveRegion activeRegion,

        @Schema(description = "전체 슬롯 수 (1~5). null이면 변경 없음")
        @Min(1) @Max(5)
        Integer totalSlotCount,

        @Schema(description = "가용 슬롯 수 (0~5, totalSlotCount 이하). null이면 변경 없음")
        @Min(0) @Max(5)
        Integer availableSlotCount,

        @Schema(description = "팀 작업 경험 (최대 4개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 4)
        List<@NotNull TeamExperience> teamExperiences,

        @Schema(description = "작화 스타일 (최대 14개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 14)
        List<@NotNull DrawingStyle> drawingStyles,

        @Schema(description = "작업 스타일. null이면 변경 없음")
        WorkPace workPace,

        @Schema(description = "투입 가능 시기. null이면 변경 없음")
        AvailableStartPeriod availableStartPeriod,

        @Schema(description = "희망 담당 업무 (최대 23개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 23)
        List<@NotNull DesiredRole> desiredRoles,

        @Schema(description = "희망 장르 (최대 29개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 29)
        List<@NotNull DesiredGenre> desiredGenres,

        @Schema(description = "희망 채용 형태 (최대 5개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 5)
        List<@NotNull DesiredEmploymentType> desiredEmploymentTypes,

        @Schema(description = "희망 근무 방식. null이면 변경 없음")
        DesiredWorkLocation desiredWorkLocation,

        @Schema(description = "선호 피드백 방식 (최대 7개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 7)
        List<@NotNull FeedbackPreference> feedbackPreferences,

        @Schema(description = "희망 MG. null이면 변경 없음")
        DesiredMinimumGuarantee desiredMinimumGuarantee,

        @Schema(description = "희망 연봉. null이면 변경 없음")
        DesiredAnnualSalary desiredAnnualSalary,

        @Schema(description = "직접입력 태그 (항목당 최대 10개, 값은 최대 10자). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 30)
        List<@NotNull CustomTagInfo> customTags,

        // 전화번호 또는 이메일을 단일 필드로 통합 수신
        @Schema(description = "연락처 (전화번호 또는 이메일, 빈 문자열로 전송 시 삭제)", example = "010-1234-5678")
        @Size(max = 100)
        @Pattern(
                regexp = "^$|^(01[016789]-\\d{3,4}-\\d{4}|[\\w.%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,})$",
                message = "전화번호(010-0000-0000) 또는 이메일 형식으로 입력해주세요"
        )
        String contact,

        @Size(max = 200)
        String sns,

        @Size(max = 200)
        String tools,

        @Size(max = 64, message = "시간대 값이 올바르지 않습니다")
        @Schema(description = "IANA 시간대 ID, 예: \"Asia/Tokyo\". null이면 변경 없음", example = "Asia/Seoul")
        String timezone,

        @Pattern(regexp = "^[A-Z]{2}$", message = "국가 코드는 ISO 3166-1 alpha-2 형식이어야 합니다")
        @Schema(description = "거주 국가 (ISO 3166-1 alpha-2). null이면 변경 없음", example = "KR")
        String countryCode
) {
}
