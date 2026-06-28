package com.ligitabl.api.rest.contest.deletecontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.LeaderboardRepo;

@ExtendWith(MockitoExtension.class)
class DeleteContestUseCaseTest {

    @Mock ContestRepo contestRepo;
    @Mock EntryRepo entryRepo;
    @Mock LeaderboardRepo leaderboardRepo;

    private DeleteContestUseCase useCase;

    private UUID ownerId;
    private UUID contestId;
    private UUID seasonId;
    private Contest contest;

    @BeforeEach
    void setUp() {
        useCase = new DeleteContestUseCase(contestRepo, entryRepo, leaderboardRepo);
        ownerId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        contest = Contest.builder()
                .id(contestId).seasonId(seasonId).name("Test")
                .isOpen(true).ownerId(ownerId)
                .fromRoundPosition(1).toRoundPosition(10)
                .build();
    }

    @Test
    void contestNotFound_returnsContestNotFoundError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.empty());

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(DeleteContestUseCase.Error.ContestNotFound.class);
    }

    @Test
    void nonOwnerTriesToDelete_returnsNotOwnerError() {
        UUID stranger = UUID.randomUUID();
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));

        var result = useCase.execute(contestId, stranger);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(DeleteContestUseCase.Error.NotOwner.class);
    }

    @Test
    void ownerDeletesContestWithOneMember_noScoredRounds_succeeds() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.countActiveByContestId(contestId)).thenReturn(1);

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isRight()).isTrue();
        verify(entryRepo).deleteByContestId(contestId);
        verify(contestRepo).delete(contestId);
    }

    @Test
    void ownerDeletesContestWithZeroMembers_succeeds() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.countActiveByContestId(contestId)).thenReturn(0);

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isRight()).isTrue();
        verify(entryRepo).deleteByContestId(contestId);
        verify(contestRepo).delete(contestId);
    }

    @Test
    void twoOrMoreMembersWithScoredRounds_returnsDeleteBlockedError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.countActiveByContestId(contestId)).thenReturn(2);
        when(leaderboardRepo.resolveEffectiveToRound(seasonId, 1, 10)).thenReturn(5);

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(DeleteContestUseCase.Error.DeleteBlocked.class);
        verify(entryRepo, never()).deleteByContestId(any());
        verify(contestRepo, never()).delete(any());
    }

    @Test
    void twoMembersButNoScoredRounds_deleteSucceeds() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.countActiveByContestId(contestId)).thenReturn(2);
        when(leaderboardRepo.resolveEffectiveToRound(seasonId, 1, 10)).thenReturn(null);

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isRight()).isTrue();
        verify(entryRepo).deleteByContestId(contestId);
        verify(contestRepo).delete(contestId);
    }
}
