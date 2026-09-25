package com.atcrew.recruit.internal.domain;

import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.CreateTeamPostingCommand;
import com.atcrew.recruit.UpdateJobPostingCommand;
import com.atcrew.recruit.UpdateTeamPostingCommand;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 마감일의 과거 여부는 작성자 시간대 기준 오늘(today)로 판정한다 — 서버 기본 시간대(UTC)가 아니다.
class PostingDeadlineTest {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Test
    void 구인글_작성_오늘_마감은_허용() {
        JobPosting posting = JobPosting.create("author", jobCreate(TODAY), TODAY);

        assertThat(posting.getDeadline()).isEqualTo(TODAY);
    }

    @Test
    void 구인글_작성_어제_마감은_거부() {
        assertDeadlineInPast(() -> JobPosting.create("author", jobCreate(TODAY.minusDays(1)), TODAY));
    }

    @Test
    void 구인글_작성_상시모집은_검증하지_않음() {
        JobPosting posting = JobPosting.create("author", jobCreate(null), TODAY);

        assertThat(posting.getDeadline()).isNull();
    }

    @Test
    void 구인글_수정_어제_마감은_거부() {
        JobPosting posting = JobPosting.create("author", jobCreate(null), TODAY);
        UpdateJobPostingCommand command = mock(UpdateJobPostingCommand.class);
        when(command.deadline()).thenReturn(TODAY.minusDays(1));

        assertDeadlineInPast(() -> posting.updateContent(command, TODAY));
    }

    @Test
    void 팀원모집글_작성_어제_마감은_거부() {
        assertDeadlineInPast(() -> TeamPosting.create("author", teamCreate(TODAY.minusDays(1)), TODAY));
    }

    @Test
    void 팀원모집글_작성_오늘_마감은_허용() {
        TeamPosting posting = TeamPosting.create("author", teamCreate(TODAY), TODAY);

        assertThat(posting.getDeadline()).isEqualTo(TODAY);
    }

    @Test
    void 팀원모집글_수정_어제_마감은_거부() {
        TeamPosting posting = TeamPosting.create("author", teamCreate(null), TODAY);
        UpdateTeamPostingCommand command = mock(UpdateTeamPostingCommand.class);
        when(command.deadline()).thenReturn(TODAY.minusDays(1));

        assertDeadlineInPast(() -> posting.updateContent(command, TODAY));
    }

    private static void assertDeadlineInPast(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(RecruitException.class)
                .extracting(e -> ((RecruitException) e).getCode())
                .isEqualTo(RecruitErrorCode.DEADLINE_IN_PAST.name());
    }

    private static CreateJobPostingCommand jobCreate(LocalDate deadline) {
        CreateJobPostingCommand command = mock(CreateJobPostingCommand.class);
        when(command.title()).thenReturn("구인글");
        when(command.deadline()).thenReturn(deadline);
        return command;
    }

    private static CreateTeamPostingCommand teamCreate(LocalDate deadline) {
        CreateTeamPostingCommand command = mock(CreateTeamPostingCommand.class);
        when(command.title()).thenReturn("팀원모집글");
        when(command.deadline()).thenReturn(deadline);
        return command;
    }
}
