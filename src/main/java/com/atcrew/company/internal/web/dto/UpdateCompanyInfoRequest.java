package com.atcrew.company.internal.web.dto;

import com.atcrew.company.ActivityField;
import com.atcrew.company.CompanyType;
import com.atcrew.company.RecruitStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record UpdateCompanyInfoRequest(
        @Schema(description = "구인구직 상태. null이면 변경 없음")
        RecruitStatus recruitStatus, // 구인구직 상태

        @Schema(description = "회사 형태. null이면 변경 없음")
        CompanyType companyType, // 회사 형태

        @Schema(description = "활동 분야 (단일 선택). null이면 변경 없음")
        ActivityField activityField, // 활동 분야

        // 전화번호 또는 이메일을 단일 필드로 통합 수신 (member와 동일 규칙)
        @Schema(description = "연락처 (전화번호 또는 이메일, 빈 문자열로 전송 시 삭제)", example = "010-1234-5678")
        @Size(max = 100)
        @Pattern(
                regexp = "^$|^(01[016789]-\\d{3,4}-\\d{4}|[\\w.%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,})$",
                message = "전화번호(010-0000-0000) 또는 이메일 형식으로 입력해주세요"
        )
        String contact, // 연락처

        @Schema(description = "SNS 링크. null이면 변경 없음", example = "https://instagram.com/atcrew")
        @Size(max = 200)
        String sns, // SNS 링크

        @Schema(description = "사업자 등록 여부(자기 신고). null이면 변경 없음")
        Boolean hasBusinessRegistration // 사업자 등록 여부
) {
}
