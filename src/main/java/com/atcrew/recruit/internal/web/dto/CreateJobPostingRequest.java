package com.atcrew.recruit.internal.web.dto;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;
import com.atcrew.recruit.JobEmploymentType;
import com.atcrew.recruit.JobPaymentType;
import com.atcrew.recruit.JobPaymentUnit;
import com.atcrew.recruit.JobWorkLocationType;
import com.atcrew.recruit.JobWorkScheduleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreateJobPostingRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String companyName,
        @Size(max = 100) String ceoName,
        @Size(max = 100) String industry,
        @Size(max = 300) String address,
        @Size(max = 100) String contact,
        @Size(max = 500) String websiteUrl,
        @Size(max = 5000) String companyDescription,
        boolean isBusinessRegistered,
        boolean isResumeRequired,
        boolean isCoverLetterRequired,
        @Size(max = 20) List<@NotNull ArtworkRole> roles,
        @Size(max = 20) List<@NotNull Genre> genres,
        @Size(max = 500) String workScope,
        // 과거 여부는 작성자 시간대 기준으로 도메인에서 검증한다 — @FutureOrPresent는 서버 시간대(UTC)의 오늘을 쓴다
        LocalDate deadline,
        @Min(1) @Max(9999) Integer recruitCount,
        @Size(max = 5000) String hiringProcess,
        @Size(max = 200) String education,
        @Size(max = 200) String experience,
        @Size(max = 100) String age,
        @Size(max = 50) String gender,
        JobEmploymentType employmentType,
        JobWorkLocationType workLocationType,
        JobWorkScheduleType workScheduleType,
        LocalTime coreTimeStart,
        LocalTime coreTimeEnd,
        boolean hasOvertimePay,
        boolean hasSocialInsurance,
        boolean hasContract,
        JobPaymentType paymentType,
        JobPaymentUnit paymentUnit,
        @Min(0) Long minAmount,
        @Min(0) Long maxAmount,
        boolean isNegotiable,
        @Min(0) Long mgAmount,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal rsRatio,
        boolean hasBuyout,
        @Size(max = 5000) String benefitDescription,
        @Size(max = 20) List<@NotBlank @Size(max = 50) String> benefitKeywords,
        @Size(max = 500) String thumbnailImage,
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> referenceImages,
        boolean submit
) {
}
