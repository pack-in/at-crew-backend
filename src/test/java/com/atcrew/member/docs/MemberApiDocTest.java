package com.atcrew.member.docs;

import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 API 문서화 통합 테스트.
 *
 * <p>실제 MariaDB Testcontainer와 전체 Spring 컨텍스트를 기동해
 * 핸들 조회·이름 수정·경력 추가 API의 요청/응답 구조를 REST Docs 스니펫으로 생성한다.
 */
class MemberApiDocTest extends RestDocsIntegrationSupport {

    @Autowired
    MemberService memberService;

    /**
     * 핸들로 회원 조회 성공 시나리오 문서화.
     * MemberService.register()로 멤버를 생성하고 공개 엔드포인트를 호출한다.
     */
    @Test
    void 핸들로_회원_조회_성공_문서화() throws Exception {
        // 조회 대상 멤버 생성
        String handle = "dochandle" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        memberService.register(
                "doc-member-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                handle,
                "문서화멤버",
                CreatorRole.WEBTOON
        );

        mockMvc.perform(get("/api/members/{handle}", handle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("member/get-by-handle",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("handle").description("회원 핸들 (@ 제외, 예: creator_kim)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("회원 고유 식별자"),
                                fieldWithPath("data.handle").description("회원 핸들 (@아이디)"),
                                fieldWithPath("data.name").description("이름·작가명"),
                                fieldWithPath("data.creatorRole").description("창작자 유형 (WEBTOON·ILLUSTRATION·WEBNOVEL·OTHER)"),
                                fieldWithPath("data.employmentStatus").description("구직 상태"),
                                fieldWithPath("data.totalSlotCount").description("총 슬롯 수"),
                                fieldWithPath("data.availableSlotCount").description("가용 슬롯 수"),
                                fieldWithPath("data.createdAt").description("가입 일시 (ISO 8601)"),
                                fieldWithPath("data.updatedAt").description("최종 수정 일시 (ISO 8601)")
                        )
                ));
    }

    /**
     * 이름 수정 성공 시나리오 문서화.
     * 회원가입 API로 토큰을 발급받은 후 인증이 필요한 이름 수정 엔드포인트를 호출한다.
     */
    @Test
    void 이름_수정_성공_문서화() throws Exception {
        // 회원가입으로 액세스 토큰 획득
        String uniqueEmail = "doc-name-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "원래이름",
                                true, true, true, false, "Asia/Seoul", "KR"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        // 이름 수정 API 호출 및 문서화
        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(new UpdateNameRequest("수정된이름"))))
                .andExpect(status().isNoContent())
                .andDo(document("member/update-name",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("name").description("변경할 이름·작가명 (최대 16자, 공백만인 값 불가)")
                        )
                ));
    }

    /**
     * 경력 추가 성공 시나리오 문서화.
     * 회원가입 API로 토큰을 발급받은 후 경력 추가 엔드포인트를 호출한다.
     */
    @Test
    void 경력_추가_성공_문서화() throws Exception {
        // 회원가입으로 액세스 토큰 획득
        String uniqueEmail = "doc-career-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "경력문서유저",
                                true, true, true, false, "Asia/Seoul", "KR"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        // 경력 추가 API 호출 및 문서화
        mockMvc.perform(post("/api/members/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(new AddCareerRequest(
                                "테스트작품", "작화", "2024.01.01", null, true, null
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("member/add-career",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("workTitle").description("참여작 이름 (최대 100자)"),
                                fieldWithPath("role").description("담당 업무 (예: 작화, 리깅, 콘티)").optional(),
                                fieldWithPath("startDate").description("작업 시작일 (yyyy.MM.dd 형식)"),
                                fieldWithPath("endDate").description("작업 종료일 (연재중이면 null)").optional(),
                                fieldWithPath("ongoing").description("연재중 여부"),
                                fieldWithPath("description").description("작업 내용 설명 (최대 200자)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("경력 고유 식별자"),
                                fieldWithPath("data.workTitle").description("참여작 이름"),
                                fieldWithPath("data.role").description("담당 업무").optional(),
                                fieldWithPath("data.startDate").description("작업 시작일 (yyyy.MM.dd)"),
                                fieldWithPath("data.ongoing").description("연재중 여부"),
                                fieldWithPath("data.periodDisplay").description("경력 기간 표시 문자열")
                        )
                ));
    }

    // ─── 요청 바디 내부 레코드 ───────────────────────────────────────────

    /** 이메일 회원가입 요청 바디 */
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

    /** 이름 수정 요청 바디 */
    record UpdateNameRequest(String name) {}

    /** 경력 추가 요청 바디 */
    record AddCareerRequest(
            String workTitle,
            String role,
            String startDate,
            String endDate,
            boolean ongoing,
            String description
    ) {}
}
