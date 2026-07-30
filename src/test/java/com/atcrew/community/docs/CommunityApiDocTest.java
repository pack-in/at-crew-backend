package com.atcrew.community.docs;

import com.atcrew.member.ActivityField;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티 API 문서화 통합 테스트.
 *
 * <p>실제 MongoDB Testcontainer와 전체 Spring 컨텍스트를 기동해
 * 배너·작가 찾아보기·구인글/팀원모집글 탭의 요청/응답 구조를 REST Docs 스니펫으로 생성한다.
 */
class CommunityApiDocTest extends RestDocsIntegrationSupport {

    @Autowired
    MemberService memberService;

    @Test
    void 배너_등록_후_목록_조회_성공_문서화() throws Exception {
        // 배너 등록은 인증된 회원이면 누구나 호출 가능 (RBAC 미도입 — BannerController TODO 참고)
        String uniqueEmail = "banner-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "배너문서유저",
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        String responseBody = registerResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseBody).at("/data/accessToken").asText();
        String ownerId = objectMapper.readTree(responseBody).at("/data/member/id").asText();

        mockMvc.perform(post("/api/community/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberId", ownerId,
                                "imageUrl", "https://img.atcrew.co.kr/banners/sample.png",
                                "linkUrl", "https://atcrew.co.kr/artworks/sample",
                                "sortOrder", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("community/create-banner",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("memberId").description("배너 등록 대상 회원 ID"),
                                fieldWithPath("imageUrl").description("배너 이미지 URL"),
                                fieldWithPath("linkUrl").description("클릭 시 이동 경로"),
                                fieldWithPath("sortOrder").description("노출 순서 (생략 시 마지막 순번 자동 부여)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("배너 ID"),
                                fieldWithPath("data.sortOrder").description("노출 순서"),
                                fieldWithPath("data.status").description("배너 상태 (ACTIVE·INACTIVE·DELETED)")
                        )
                ));

        mockMvc.perform(get("/api/community/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("community/list-banners",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data[].id").description("배너 ID"),
                                fieldWithPath("data[].imageUrl").description("배너 이미지 URL"),
                                fieldWithPath("data[].sortOrder").description("노출 순서")
                        )
                ));
    }

    @Test
    void 작가_찾아보기_성공_문서화() throws Exception {
        MemberInfo author = memberService.register(
                "author-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "authordoc" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                "찾아보기작가", CreatorRole.WEBTOON);
        memberService.updateInfo(author.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.THREE_TO_FOUR,
                null, null, null, null, null, null, null, null, null));

        mockMvc.perform(get("/api/community/authors").param("activityField", "WEBTOON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("community/list-authors",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("회원 ID"),
                                fieldWithPath("data.items[].handle").description("회원 핸들"),
                                fieldWithPath("data.items[].employmentStatus").description("구인구직 상태"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 구인글_탭_빈_목록_문서화() throws Exception {
        // recruit 모듈 미구현 — NoopRecruitFeedPort가 항상 빈 목록을 반환한다.
        mockMvc.perform(get("/api/community/job-postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andDo(document("community/list-job-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items").description("구인글 카드 목록 (recruit 모듈 미구현으로 항상 빈 배열)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    /** 이메일 회원가입 요청 바디 (accessToken 발급용) */
    record RegisterRequest(
            String email,
            String password,
            String passwordConfirm,
            String name,
            boolean agreeService,
            boolean agreePrivacy,
            boolean agreeThirdParty,
            boolean agreeMarketing,
            String timezone,
            String countryCode
    ) {}
}
