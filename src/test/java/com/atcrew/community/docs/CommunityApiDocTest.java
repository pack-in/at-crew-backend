package com.atcrew.community.docs;

import com.atcrew.community.BannerService;
import com.atcrew.community.CreateBannerCommand;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
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

    @Autowired
    BannerService bannerService;

    @Test
    void 배너_목록_조회_성공_문서화() throws Exception {
        // 배너 등록 API는 관리자 권한 체계 도입 전까지 SecurityConfig가 막아 두므로(403) 서비스로 직접 만든다.
        String uniqueEmail = "banner-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MemberInfo owner = memberService.register(uniqueEmail,
                "bannerdoc" + UUID.randomUUID().toString().substring(0, 4), "배너문서유저");
        bannerService.createBanner(new CreateBannerCommand(owner.id(),
                "https://img.atcrew.co.kr/banners/sample.png", "https://atcrew.co.kr/artworks/sample", 0));

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
                                fieldWithPath("data.page").description("현재 페이지 번호 (1부터)"),
                                fieldWithPath("data.size").description("페이지 크기"),
                                fieldWithPath("data.totalCount").description(
                                        "전체 작가 수 (현재 필터와 뷰어 언어 세그먼트 기준)"),
                                fieldWithPath("data.totalPages").description("전체 페이지 수"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 포트폴리오_탭_정렬_조회_문서화() throws Exception {
        // 정렬 기준별 페이지 순회 정확성은 ArtworkSortModuleTests가 검증한다 — 여기서는 파라미터 계약과
        // 응답 구조를 문서로 남긴다.
        mockMvc.perform(get("/api/community/artworks")
                        .param("sort", "VIEW_COUNT")
                        .param("page", "1")
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
                                parameterWithName("page").description(
                                        "페이지 번호 (1부터, 기본 1). page × size가 10000을 넘으면 400 INVALID_PAGE").optional(),
                                parameterWithName("size").description("페이지 크기 (기본 20, 최대 50)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items").description("작품 카드 목록 (공개·이미지 처리 완료 작품만 노출). 카드 링크는 각 항목의 authorHandle·publicCode로 "
                                        + "/{authorHandle}/artwork/{publicCode}를 조립한다"),
                                fieldWithPath("data.page").description("현재 페이지 번호 (1부터)"),
                                fieldWithPath("data.size").description("페이지 크기"),
                                fieldWithPath("data.totalCount").description("전체 작품 수 (현재 필터·뷰어 조건 기준)"),
                                fieldWithPath("data.totalPages").description("전체 페이지 수"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 이번_주_가장_핫한_작품_조회_문서화() throws Exception {
        // 정렬·채움·필터 규칙은 HotArtworkTests가 검증한다 — 여기서는 응답 구조(목록, 최대 6개)를 문서로 남긴다.
        mockMvc.perform(get("/api/community/artworks/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andDo(document("community/list-hot-artworks",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data").description(
                                        "작품 카드 목록 (최대 6개). 최근 7일 기간 조회수 → 북마크 수 → 최신 등록순이며, "
                                                + "기간 조회수가 있는 작품이 6개 미만이면 조회수가 없는 작품으로 채운다. "
                                                + "노출 조건은 포트폴리오 탭 목록과 같다. 카드 링크는 각 항목의 authorHandle·publicCode로 "
                                                + "/{authorHandle}/artwork/{publicCode}를 조립한다")
                        )
                ));
    }

    @Disabled("MVP 범위 밖 — recruit(구인·구직·팀원모집) 미출시, 출시 시 해제")
    @Test
    void 구인글_탭_빈_목록_문서화() throws Exception {
        // recruit 모듈의 RecruitService를 호출한다 — 이 테스트에는 PUBLISHED 구인글이 없어 빈 목록이 반환된다.
        mockMvc.perform(get("/api/community/job-postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andDo(document("community/list-job-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items").description("구인글 카드 목록 (PUBLISHED 상태만 노출)"),
                                fieldWithPath("data.page").description("현재 페이지 번호 (1부터)"),
                                fieldWithPath("data.size").description("페이지 크기"),
                                fieldWithPath("data.totalCount").description("PUBLISHED 구인글 전체 수"),
                                fieldWithPath("data.totalPages").description("전체 페이지 수"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 팀원모집글_탭_빈_목록_문서화() throws Exception {
        // 구인글 탭과 같은 계약이다 — 이 테스트에는 PUBLISHED 팀원모집글이 없어 빈 목록이 반환된다.
        mockMvc.perform(get("/api/community/team-recruits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andDo(document("community/list-team-recruits",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items").description("팀원모집글 카드 목록 (PUBLISHED 상태만 노출)"),
                                fieldWithPath("data.page").description("현재 페이지 번호 (1부터)"),
                                fieldWithPath("data.size").description("페이지 크기"),
                                fieldWithPath("data.totalCount").description("PUBLISHED 팀원모집글 전체 수"),
                                fieldWithPath("data.totalPages").description("전체 페이지 수"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }
}
