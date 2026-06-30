package com.ligitabl.api.rest.season.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.repo.CompetitionRepo;

@ExtendWith(MockitoExtension.class)
class RevertSeasonUseCaseTest {

    @Mock CompetitionRepo competitionRepo;

    private RevertSeasonUseCase useCase;
    private UUID competitionId;
    private UUID activeSeasonId;
    private UUID formerSeasonId;

    @BeforeEach
    void setUp() {
        useCase = new RevertSeasonUseCase(competitionRepo);
        competitionId = UUID.randomUUID();
        activeSeasonId = UUID.randomUUID();
        formerSeasonId = UUID.randomUUID();
    }

    @Test
    void competitionNotFound_returnsError() {
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.empty());

        var result = useCase.execute("premier-league");

        assertThat(result).isInstanceOf(RevertSeasonUseCase.Result.CompetitionNotFound.class);
    }

    @Test
    void noFormerSeason_returnsError() {
        Competition competition = buildCompetition(null);
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));

        var result = useCase.execute("premier-league");

        assertThat(result).isInstanceOf(RevertSeasonUseCase.Result.NoFormerSeason.class);
        verify(competitionRepo, never()).revertToFormerSeason(any(), any(), any());
    }

    @Test
    void formerSeasonExists_reverts() {
        Competition competition = buildCompetition(formerSeasonId);
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));

        var result = useCase.execute("premier-league");

        assertThat(result).isInstanceOf(RevertSeasonUseCase.Result.Ok.class);
        assertThat(((RevertSeasonUseCase.Result.Ok) result).newActiveSeasonId()).isEqualTo(formerSeasonId);
        verify(competitionRepo).revertToFormerSeason(competitionId, formerSeasonId, activeSeasonId);
    }

    private Competition buildCompetition(UUID formerId) {
        return Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(activeSeasonId)
                .formerSeasonId(formerId)
                .build();
    }
}
