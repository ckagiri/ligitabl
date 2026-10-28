package com.ligitabl.api.usecase.season.autoactivate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.api.usecase.season.admin.ActivateSeasonUseCase;
import com.ligitabl.api.usecase.shared.AdminAlerts;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class AutoActivateSeasonUseCaseTest {

    private static final OffsetDateTime NOW = TestClock.NOW.atOffset(ZoneOffset.UTC);

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    AdminAlerts adminAlerts;

    private AutoActivateSeasonUseCase useCase;
    private UUID competitionId;
    private UUID activeSeasonId;

    @BeforeEach
    void setUp() {
        // Real delegate, so the promotion call itself is asserted rather than a mock interaction.
        useCase = new AutoActivateSeasonUseCase(
                competitionRepo,
                seasonRepo,
                new ActivateSeasonUseCase(competitionRepo, seasonRepo),
                adminAlerts,
                TestClock.FIXED);
        competitionId = UUID.randomUUID();
        activeSeasonId = UUID.randomUUID();
    }

    @Test
    void competitionNotFound() {
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.empty());

        var result = useCase.execute("premier-league");

        assertThat(result).isEqualTo(new AutoActivateSeasonResult.CompetitionNotFound("premier-league"));
    }

    @Test
    void activeSeasonInPlay_notDue() {
        givenActiveSeason(buildInPlaySeason(activeSeasonId), UUID.randomUUID());

        var result = useCase.execute("premier-league");

        assertThat(result).isInstanceOf(AutoActivateSeasonResult.NotDue.class);
        verify(competitionRepo, never()).promoteUpcomingSeason(any(), any(), any());
    }

    @Test
    void completedButPreSeasonNotYetOpen_notDue() {
        givenActiveSeason(buildCompletedSeason(activeSeasonId, NOW.plusDays(1)), UUID.randomUUID());

        var result = useCase.execute("premier-league");

        assertThat(result).isInstanceOf(AutoActivateSeasonResult.NotDue.class);
        verify(competitionRepo, never()).promoteUpcomingSeason(any(), any(), any());
    }

    @Test
    void dueButNoUpcomingSeasonAssigned_noSwitch() {
        givenActiveSeason(buildCompletedSeason(activeSeasonId, NOW.minusDays(1)), null);

        var result = useCase.execute("premier-league");

        assertThat(result).isInstanceOf(AutoActivateSeasonResult.NoUpcomingSeason.class);
        verify(competitionRepo, never()).promoteUpcomingSeason(any(), any(), any());
        verifyNoInteractions(adminAlerts);
    }

    @Test
    void dueAndUpcomingAssigned_promotesAndAlerts() {
        UUID upcomingId = UUID.randomUUID();
        givenActiveSeason(buildCompletedSeason(activeSeasonId, NOW.minusDays(1)), upcomingId);

        var result = useCase.execute("premier-league");

        assertThat(result).isEqualTo(new AutoActivateSeasonResult.Activated(upcomingId, activeSeasonId));
        verify(competitionRepo).promoteUpcomingSeason(competitionId, upcomingId, activeSeasonId);
        verify(adminAlerts).notifySeasonActivated("premier-league", upcomingId, activeSeasonId);
    }

    @Test
    void alreadySwitched_idempotent() {
        // After a switch the active season is the incoming one: not completed, and its pre-season
        // opening is not read as "promote my successor".
        Season alreadySwitchedSeason = Season.builder()
                .id(activeSeasonId)
                .clientId(1)
                .competitionId(competitionId)
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(TestClock.TODAY.plusMonths(1))
                .endDate(TestClock.TODAY.plusMonths(10))
                .completed(false)
                .preSeasonOpensAt(null)
                .initialRankings(List.of())
                .build();
        givenActiveSeason(alreadySwitchedSeason, null);

        var result = useCase.execute("premier-league");

        assertThat(result).isInstanceOf(AutoActivateSeasonResult.NotDue.class);
        verify(competitionRepo, never()).promoteUpcomingSeason(any(), any(), any());
    }

    private void givenActiveSeason(Season activeSeason, UUID upcomingSeasonId) {
        Competition competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(activeSeasonId)
                .upcomingSeasonId(upcomingSeasonId)
                .build();
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findById(activeSeasonId)).thenReturn(Optional.of(activeSeason));
    }

    /** Not completed, still within its start/end window. */
    private Season buildInPlaySeason(UUID id) {
        return Season.builder()
                .id(id)
                .clientId(1)
                .competitionId(competitionId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .startDate(TestClock.TODAY.minusMonths(9))
                .endDate(TestClock.TODAY.plusMonths(1))
                .completed(false)
                .initialRankings(List.of())
                .build();
    }

    private Season buildCompletedSeason(UUID id, OffsetDateTime preSeasonOpensAt) {
        return Season.builder()
                .id(id)
                .clientId(1)
                .competitionId(competitionId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .startDate(TestClock.TODAY.minusMonths(9))
                .endDate(TestClock.TODAY.minusDays(1))
                .completed(true)
                .preSeasonOpensAt(preSeasonOpensAt)
                .initialRankings(List.of())
                .build();
    }
}
