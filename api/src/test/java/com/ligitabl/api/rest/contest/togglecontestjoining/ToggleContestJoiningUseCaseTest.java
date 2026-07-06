package com.ligitabl.api.rest.contest.togglecontestjoining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.repo.ContestRepo;

@ExtendWith(MockitoExtension.class)
class ToggleContestJoiningUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    @Mock
    ContestSeasonSupport contestSeasonSupport;

    private ToggleContestJoiningUseCase useCase;

    private UUID ownerId;
    private UUID contestId;
    private Contest contest;

    @BeforeEach
    void setUp() {
        useCase = new ToggleContestJoiningUseCase(contestRepo, contestSeasonSupport);
        ownerId = UUID.randomUUID();
        contestId = UUID.randomUUID();

        contest = Contest.builder()
                .id(contestId)
                .seasonId(UUID.randomUUID())
                .name("Test")
                .isOpen(true)
                .ownerId(ownerId)
                .build();
    }

    @Test
    void contestNotFound_returnsError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.empty());

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ToggleContestJoiningUseCase.Error.ContestNotFound.class);
    }

    @Test
    void notOwner_returnsError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));

        var result = useCase.execute(contestId, UUID.randomUUID());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ToggleContestJoiningUseCase.Error.NotOwner.class);
    }

    @Test
    void pastSeasonContest_returnsError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(contestSeasonSupport.isPastSeason(contest)).thenReturn(true);

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ToggleContestJoiningUseCase.Error.PastSeasonContest.class);
    }

    @Test
    void happyPath_togglesOpenState() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(contestSeasonSupport.isPastSeason(contest)).thenReturn(false);
        when(contestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().isOpen()).isFalse();
    }
}
