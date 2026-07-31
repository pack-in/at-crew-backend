package com.atcrew.recruit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 구인글 수정 커맨드 (docs/design/recruit-module-design.md §4.1). 부분 업데이트 — null 필드는 기존 값을 유지한다.
 * boolean 필드는 null 허용을 위해 wrapper 타입({@link Boolean})을 사용한다.
 */
public record UpdateJobPostingCommand(
        String title,                           // 공고 제목
        String companyName,                     // 회사명
        String ceoName,                         // 대표자명
        String industry,                        // 업종
        String address,                         // 주소
        String contact,                         // 연락처
        String websiteUrl,                      // 홈페이지 URL
        String companyDescription,              // 회사 소개
        Boolean isBusinessRegistered,           // 사업자등록 여부
        Boolean isResumeRequired,               // 이력서 필수 여부
        Boolean isCoverLetterRequired,          // 자기소개서 필수 여부
        List<String> roles,                     // 모집 역할
        List<String> genres,                    // 모집 장르
        String workScope,                       // 작업 범위
        LocalDate deadline,                     // 마감일 (null이면 상시모집)
        Integer recruitCount,                   // 모집 인원
        String hiringProcess,                   // 채용 절차
        String education,                       // 학력 요건 (자유 텍스트)
        String experience,                      // 경력 요건 (자유 텍스트)
        String age,                             // 연령 요건 (자유 텍스트)
        String gender,                          // 성별 요건 (자유 텍스트)
        JobEmploymentType employmentType,       // 고용 형태
        JobWorkLocationType workLocationType,   // 근무지 형태
        JobWorkScheduleType workScheduleType,   // 근무 형태
        LocalTime coreTimeStart,                // 코어타임 시작 (FLEXIBLE일 때만 사용)
        LocalTime coreTimeEnd,                  // 코어타임 종료 (FLEXIBLE일 때만 사용)
        Boolean hasOvertimePay,                 // 야근수당 지급 여부
        Boolean hasSocialInsurance,             // 4대보험 가입 여부
        Boolean hasContract,                    // 근로계약서 작성 여부
        JobPaymentType paymentType,             // 급여 지급 방식
        JobPaymentUnit paymentUnit,             // 급여 지급 단위
        Long minAmount,                         // 최소 금액
        Long maxAmount,                         // 최대 금액
        Boolean isNegotiable,                   // 협의 가능 여부
        Long mgAmount,                          // MG(미니멈개런티) 금액
        BigDecimal rsRatio,                     // RS(러닝개런티) 비율(%)
        Boolean hasBuyout,                      // 매절 여부
        String benefitDescription,              // 복지 설명
        List<String> benefitKeywords,           // 복지 키워드 (표시 전용)
        String thumbnailImage,                  // 썸네일 이미지 URL
        List<String> referenceImages            // 참고 이미지 URL 목록 (표시 전용)
) {
}
