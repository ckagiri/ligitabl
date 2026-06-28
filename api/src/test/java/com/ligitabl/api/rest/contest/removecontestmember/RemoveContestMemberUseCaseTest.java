package com.ligitabl.api.rest.contest.removecontestmember;

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
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;

@ExtendWith(MockitoExtension.class)
class RemoveContestMemberUseCaseTest {

    @Mock ContestRepo contestRepo;
    @Mock EntryRepo entryRepo;

    private RemoveContestMemberUseCase useCase;

    private UUID ownerId;
    private UUID memberId;
    private UUID contestId;
    private UUID seasonId;
    private Contest contest;
    private Entry activeEntry;

    @BeforeEach
    void setUp() {
        useCase = new RemoveContestMemberUseCase(contestRepo, entryRepo);
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        contest = Contest.builder()
                .id(contestId).seasonId(seasonId).name("Test").isOpen(true).ownerId(ownerId).build();

        activeEntry = Entry.builder()
                .userId(memberId).contestId(contestId).joinedAtRound(1).build();
    }

    @Test
    void requesterIsNotOwner_returnsNotOwnerError() {
        UUID nonOwner = UUID.randomUUID();
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));

        var result = useCase.execute(contestId, nonOwner, memberId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RemoveContestMemberUseCase.Error.NotOwner.class);
    }

    @Test
    void targetIsOwner_returnsCannotRemoveOwnerError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));

        var result = useCase.execute(contestId, ownerId, ownerId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RemoveContestMemberUseCase.Error.CannotRemoveOwner.class);
    }

    @Test
    void targetNotAMember_returnsNotAMemberError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(memberId, contestId)).thenReturn(Optional.empty());

        var result = useCase.execute(contestId, ownerId, memberId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RemoveContestMemberUseCase.Error.NotAMember.class);
    }

    @Test
    void targetHasNoScores_hardDeletesEntry_suggestsCodeRegen() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(memberId, contestId)).thenReturn(Optional.of(activeEntry));
        when(entryRepo.hasAnyScore(memberId, contestId)).thenReturn(false);

        var result = useCase.execute(contestId, ownerId, memberId, 5);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().shouldSuggestCodeRegen()).isTrue();
        verify(entryRepo).deleteByUserAndContest(memberId, contestId);
        verify(entryRepo, never()).softRemove(any(), any(), anyInt());
    }

    @Test
    void targetHasScores_softDeletesEntry_suggestsCodeRegen() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(memberId, contestId)).thenReturn(Optional.of(activeEntry));
        when(entryRepo.hasAnyScore(memberId, contestId)).thenReturn(true);

        var result = useCase.execute(contestId, ownerId, memberId, 5);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().shouldSuggestCodeRegen()).isTrue();
        verify(entryRepo).softRemove(memberId, contestId, 5);
        verify(entryRepo, never()).deleteByUserAndContest(any(), any());
    }

    @Test
    void contestNotFound_returnsContestNotFoundError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.empty());

        var result = useCase.execute(contestId, ownerId, memberId, 5);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RemoveContestMemberUseCase.Error.ContestNotFound.class);
    }
}
