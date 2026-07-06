package com.ligitabl.api.rest.contest.regeneratecontestcode;

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

import com.ligitabl.api.contest.ContestCodeGenerator;
import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.repo.ContestRepo;

@ExtendWith(MockitoExtension.class)
class RegenerateContestCodeUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    @Mock
    ContestCodeGenerator codeGenerator;

    @Mock
    ContestSeasonSupport contestSeasonSupport;

    private RegenerateContestCodeUseCase useCase;

    private UUID ownerId;
    private UUID contestId;
    private Contest contest;

    @BeforeEach
    void setUp() {
        useCase = new RegenerateContestCodeUseCase(contestRepo, codeGenerator, contestSeasonSupport);
        ownerId = UUID.randomUUID();
        contestId = UUID.randomUUID();

        contest = Contest.builder()
                .id(contestId)
                .seasonId(UUID.randomUUID())
                .name("Test")
                .isOpen(true)
                .joinCode("OLDCODE")
                .ownerId(ownerId)
                .build();
    }

    @Test
    void contestNotFound_returnsError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.empty());

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RegenerateContestCodeUseCase.Error.ContestNotFound.class);
    }

    @Test
    void notOwner_returnsError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));

        var result = useCase.execute(contestId, UUID.randomUUID());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RegenerateContestCodeUseCase.Error.NotOwner.class);
    }

    @Test
    void pastSeasonContest_returnsError() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(contestSeasonSupport.isPastSeason(contest)).thenReturn(true);

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RegenerateContestCodeUseCase.Error.PastSeasonContest.class);
    }

    @Test
    void happyPath_regeneratesCode() {
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(contestSeasonSupport.isPastSeason(contest)).thenReturn(false);
        when(codeGenerator.generate()).thenReturn("NEWCODE");
        when(contestRepo.findByJoinCode("NEWCODE")).thenReturn(Optional.empty());
        when(contestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(contestId, ownerId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().joinCode()).isEqualTo("NEWCODE");
    }
}
