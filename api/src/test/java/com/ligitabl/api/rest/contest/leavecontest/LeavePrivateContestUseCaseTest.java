package com.ligitabl.api.rest.contest.leavecontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;

@ExtendWith(MockitoExtension.class)
class LeavePrivateContestUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    ContestSeasonSupport contestSeasonSupport;

    private LeavePrivateContestUseCase useCase;

    private UUID ownerId;
    private UUID memberId;
    private UUID contestId;
    private UUID seasonId;
    private Contest contest;
    private Entry activeEntry;

    @BeforeEach
    void setUp() {
        useCase = new LeavePrivateContestUseCase(contestRepo, entryRepo, contestSeasonSupport);
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        contest = Contest.builder()
                .id(contestId)
                .seasonId(seasonId)
                .name("Test")
                .isOpen(true)
                .ownerId(ownerId)
                .build();

        activeEntry = Entry.builder()
                .userId(memberId)
                .contestId(contestId)
                .joinedAtRound(1)
                .build();
    }

    @Test
    void ownerTriesToLeave_returnsOwnerCannotLeaveError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));

        var result = useCase.execute(contestId, ownerId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(LeavePrivateContestUseCase.Error.OwnerCannotLeave.class);
        verify(entryRepo, never()).softRemove(any(), any(), anyInt());
        verify(entryRepo, never()).deleteByUserAndContest(any(), any());
    }

    @Test
    void nonMemberTriesToLeave_returnsNotAMemberError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(memberId, contestId)).thenReturn(Optional.empty());

        var result = useCase.execute(contestId, memberId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(LeavePrivateContestUseCase.Error.NotAMember.class);
    }

    @Test
    void softRemovedMemberTriesToLeave_returnsNotAMemberError() {
        Entry removedEntry = Entry.builder()
                .userId(memberId)
                .contestId(contestId)
                .joinedAtRound(1)
                .removedAtRound(3)
                .build();
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(memberId, contestId)).thenReturn(Optional.of(removedEntry));

        var result = useCase.execute(contestId, memberId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(LeavePrivateContestUseCase.Error.NotAMember.class);
    }

    @Test
    void activeMemberWithNoScores_hardDeletesEntry() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(memberId, contestId)).thenReturn(Optional.of(activeEntry));
        when(contestSeasonSupport.isPastSeason(contest)).thenReturn(false);
        when(contestSeasonSupport.isFinalSprintUnderway(contest, 5)).thenReturn(false);
        when(entryRepo.hasAnyScore(memberId, contestId)).thenReturn(false);

        var result = useCase.execute(contestId, memberId, 5);

        assertThat(result.isRight()).isTrue();
        verify(entryRepo).deleteByUserAndContest(memberId, contestId);
        verify(entryRepo, never()).softRemove(any(), any(), anyInt());
    }

    @Test
    void activeMemberWithScores_softDeletesEntry() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(memberId, contestId)).thenReturn(Optional.of(activeEntry));
        when(contestSeasonSupport.isPastSeason(contest)).thenReturn(false);
        when(contestSeasonSupport.isFinalSprintUnderway(contest, 5)).thenReturn(false);
        when(entryRepo.hasAnyScore(memberId, contestId)).thenReturn(true);

        var result = useCase.execute(contestId, memberId, 5);

        assertThat(result.isRight()).isTrue();
        verify(entryRepo).softRemove(memberId, contestId, 5);
        verify(entryRepo, never()).deleteByUserAndContest(any(), any());
    }

    @Test
    void pastSeasonContest_returnsPastSeasonContestError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(memberId, contestId)).thenReturn(Optional.of(activeEntry));
        when(contestSeasonSupport.isPastSeason(contest)).thenReturn(true);

        var result = useCase.execute(contestId, memberId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(LeavePrivateContestUseCase.Error.PastSeasonContest.class);
        verify(entryRepo, never()).softRemove(any(), any(), anyInt());
        verify(entryRepo, never()).deleteByUserAndContest(any(), any());
    }

    @Test
    void finalSprintUnderway_returnsFinalSprintUnderwayError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(memberId, contestId)).thenReturn(Optional.of(activeEntry));
        when(contestSeasonSupport.isPastSeason(contest)).thenReturn(false);
        when(contestSeasonSupport.isFinalSprintUnderway(contest, 5)).thenReturn(true);

        var result = useCase.execute(contestId, memberId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(LeavePrivateContestUseCase.Error.FinalSprintUnderway.class);
        verify(entryRepo, never()).softRemove(any(), any(), anyInt());
        verify(entryRepo, never()).deleteByUserAndContest(any(), any());
    }

    @Test
    void contestNotFound_returnsContestNotFoundError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.empty());

        var result = useCase.execute(contestId, memberId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(LeavePrivateContestUseCase.Error.ContestNotFound.class);
    }
}
