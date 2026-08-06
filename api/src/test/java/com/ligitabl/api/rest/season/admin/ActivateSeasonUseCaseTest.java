package com.ligitabl.api.rest.season.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class ActivateSeasonUseCaseTest {

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    SeasonRepo seasonRepo;

    private ActivateSeasonUseCase useCase;
    private UUID competitionId;
    private UUID activeSeasonId;
    private UUID upcomingSeasonId;

    @BeforeEach
    void setUp() {
        useCase = new ActivateSeasonUseCase(competitionRepo, seasonRepo);
        competitionId = UUID.randomUUID();
        activeSeasonId = UUID.randomUUID();
        upcomingSeasonId = UUID.randomUUID();
    }

    @Test
    void competitionNotFound_returnsError() {
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.empty());

        var result = useCase.execute("premier-league", null);

        assertThat(result).isInstanceOf(ActivateSeasonUseCase.Result.CompetitionNotFound.class);
    }

    @Test
    void noUpcomingSeasonAssigned_returnsError() {
        Competition competition = buildCompetition(null);
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));

        var result = useCase.execute("premier-league", null);

        assertThat(result).isInstanceOf(ActivateSeasonUseCase.Result.NoUpcomingSeason.class);
        verify(competitionRepo, never()).promoteUpcomingSeason(any(), any(), any());
    }

    @Test
    void upcomingSeasonAssigned_promotes() {
        Competition competition = buildCompetition(upcomingSeasonId);
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));

        var result = useCase.execute("premier-league", null);

        assertThat(result).isInstanceOf(ActivateSeasonUseCase.Result.Ok.class);
        assertThat(((ActivateSeasonUseCase.Result.Ok) result).newActiveSeasonId())
                .isEqualTo(upcomingSeasonId);
        verify(competitionRepo).promoteUpcomingSeason(competitionId, upcomingSeasonId, activeSeasonId);
    }

    @Test
    void withPredictionsOpenAt_updatesUpcomingSeasonDate() {
        Competition competition = buildCompetition(upcomingSeasonId);
        Season upcoming = buildSeason(upcomingSeasonId);
        OffsetDateTime predictionsOpenAt = OffsetDateTime.now().plusDays(5);

        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findById(upcomingSeasonId)).thenReturn(Optional.of(upcoming));

        var result = useCase.execute("premier-league", predictionsOpenAt);

        assertThat(result).isInstanceOf(ActivateSeasonUseCase.Result.Ok.class);
        verify(seasonRepo).save(argThat(s -> predictionsOpenAt.equals(s.getPredictionsOpenAt())));
        verify(competitionRepo).promoteUpcomingSeason(competitionId, upcomingSeasonId, activeSeasonId);
    }

    private Competition buildCompetition(UUID upcomingId) {
        return Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(activeSeasonId)
                .upcomingSeasonId(upcomingId)
                .build();
    }

    private Season buildSeason(UUID id) {
        return Season.builder()
                .id(id)
                .clientId(1)
                .competitionId(competitionId)
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(TestClock.TODAY.plusMonths(1))
                .endDate(TestClock.TODAY.plusMonths(10))
                .completed(false)
                .initialRankings(java.util.List.of())
                .build();
    }
}
