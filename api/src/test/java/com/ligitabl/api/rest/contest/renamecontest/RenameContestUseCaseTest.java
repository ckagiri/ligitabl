package com.ligitabl.api.rest.contest.renamecontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

@ExtendWith(MockitoExtension.class)
class RenameContestUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    private RenameContestUseCase useCase;

    private UUID userId;
    private UUID seasonId;
    private Contest contest;

    @BeforeEach
    void setUp() {
        useCase = new RenameContestUseCase(contestRepo);
        userId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        contest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Old Name")
                .ownerId(userId)
                .fromRoundPosition(1)
                .toRoundPosition(4)
                .build();
    }

    @Test
    void happyPath_renamesAndSaves() {
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(contestRepo.existsByOwnerSeasonPeriodAndName(seasonId, userId, 1, 4, "New Name", contest.getId()))
                .thenReturn(false);
        when(contestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new RenameContestCommand(userId, contest.getId(), "New Name"));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().name()).isEqualTo("New Name");
    }

    @Test
    void trimsWhitespace() {
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(contestRepo.existsByOwnerSeasonPeriodAndName(seasonId, userId, 1, 4, "New Name", contest.getId()))
                .thenReturn(false);
        when(contestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new RenameContestCommand(userId, contest.getId(), "  New Name  "));

        assertThat(result.get().name()).isEqualTo("New Name");
    }

    @Test
    void blankName_returnsError() {
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));

        var result = useCase.execute(new RenameContestCommand(userId, contest.getId(), "   "));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenameContestError.BlankName.class);
        verify(contestRepo, never()).save(any());
    }

    @Test
    void nameConflict_returnsError() {
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(contestRepo.existsByOwnerSeasonPeriodAndName(seasonId, userId, 1, 4, "Taken", contest.getId()))
                .thenReturn(true);

        var result = useCase.execute(new RenameContestCommand(userId, contest.getId(), "Taken"));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenameContestError.NameConflict.class);
        verify(contestRepo, never()).save(any());
    }

    @Test
    void notOwner_returnsError() {
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));

        var result = useCase.execute(new RenameContestCommand(UUID.randomUUID(), contest.getId(), "New Name"));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenameContestError.NotOwner.class);
    }

    @Test
    void contestNotFound_returnsError() {
        UUID missingId = UUID.randomUUID();
        when(contestRepo.findById(missingId)).thenReturn(Optional.empty());

        var result = useCase.execute(new RenameContestCommand(userId, missingId, "New Name"));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenameContestError.ContestNotFound.class);
    }
}
