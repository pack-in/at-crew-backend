package com.atcrew.company.internal.domain;

import com.atcrew.company.internal.exception.CompanyErrorCode;
import com.atcrew.company.internal.exception.CompanyException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 경력 날짜의 미래 여부는 작성 회원 시간대 기준 오늘(today)로 판정한다 — 서버 기본 시간대(UTC)가 아니다.
class CompanyCareerTest {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Test
    void 오늘_시작일은_허용() {
        CompanyCareer career = CompanyCareer.create("company", "작품", TODAY, null, true, null, TODAY);

        assertThat(career.getStartDate()).isEqualTo(TODAY);
    }

    @Test
    void 미래_시작일은_거부() {
        assertThatThrownBy(() -> CompanyCareer.create("company", "작품", TODAY.plusDays(1), null, true, null, TODAY))
                .isInstanceOf(CompanyException.class)
                .extracting(e -> ((CompanyException) e).getCode())
                .isEqualTo(CompanyErrorCode.CAREER_DATE_IN_FUTURE.name());
    }

    @Test
    void 미래_종료일은_거부() {
        assertThatThrownBy(() -> CompanyCareer.create("company", "작품", TODAY, TODAY.plusDays(1), false, null, TODAY))
                .isInstanceOf(CompanyException.class)
                .extracting(e -> ((CompanyException) e).getCode())
                .isEqualTo(CompanyErrorCode.CAREER_DATE_IN_FUTURE.name());
    }
}
