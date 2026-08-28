package com.atcrew.community.docs;

import com.atcrew.member.ActivityField;
import com.atcrew.member.DesiredEmploymentType;
import com.atcrew.member.DesiredGenre;
import com.atcrew.member.DesiredRole;
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
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티 API 문서화 통합 테스트.
 *
 * <p>실제 MariaDB Testcontainer와 전체 Spring 컨텍스트를 기동해
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
                                true, true, true, false, "Asia/Seoul", "KR", "KO"))))
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
                "찾아보기작가");
        // 노출 대상 7개 항목(사용자 이름·활동 분야·활동 경력·희망 담당 업무·희망 장르·희망 채용 형태·연락처)을
        // 모두 채워야 작가 찾기에 노출된다 (기획서 마이페이지_작가-R08).
        memberService.updateInfo(author.id(), new UpdateInfoCommand(
                EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.THREE_TO_FOUR,
                null, null, null, null, null, null,
                null, List.of(DesiredRole.STORYBOARD), List.of(DesiredGenre.BL),
                List.of(DesiredEmploymentType.FREELANCE), null, null, null, null, null,
                "010-1234-5678", null, null, null, null));

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
    void 포트폴리오_탭_정렬_조회_문서화() throws Exception {
        // 정렬 기준별 커서 정확성은 ArtworkSortModuleTests가 검증한다 — 여기서는 파라미터 계약과
        // 응답 구조(nextCursor 형식 포함)를 문서로 남긴다.
        mockMvc.perform(get("/api/community/artworks")
                        .param("sort", "VIEW_COUNT")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("community/list-artworks",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("artworkField").description("작품 분야 필터 (선택)").optional(),
                                parameterWithName("ageRating").description("연령 등급 필터 (선택)").optional(),
                                parameterWithName("sort").description(
                                        "정렬 기준 — LATEST(최신순, 기본)·OLDEST(오래된순)·VIEW_COUNT(조회순)·BOOKMARK_COUNT(북마크순)"),
                                parameterWithName("cursor").description(
                                        "직전 응답의 nextCursor를 그대로 전달. 정렬 기준을 바꾸면 커서 의미가 달라지므로 생략해야 한다").optional(),
                                parameterWithName("size").description("페이지 크기 (기본 20, 최대 50)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items").description("작품 카드 목록 (공개·이미지 처리 완료 작품만 노출)"),
                                fieldWithPath("data.nextCursor").description(
                                        "다음 페이지 커서 \"정렬값_작품ID\" (마지막 페이지면 null)").optional(),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 구인글_탭_빈_목록_문서화() throws Exception {
        // recruit 모듈의 RecruitService를 호출한다 — 이 테스트에는 PUBLISHED 구인글이 없어 빈 목록이 반환된다.
        mockMvc.perform(get("/api/community/job-postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andDo(document("community/list-job-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items").description("구인글 카드 목록 (PUBLISHED 상태만 노출)"),
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
            String countryCode,
            String primaryLanguage
    ) {}
}
