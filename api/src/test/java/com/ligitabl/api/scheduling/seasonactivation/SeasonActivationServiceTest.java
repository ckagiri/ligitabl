package com.ligitabl.api.scheduling.seasonactivation;

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

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class SeasonActivationServiceTest {

    private static final CompetitionDefaults DEFAULTS = new CompetitionDefaults("premier-league");

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    SeasonRepo seasonRepo;

    private SeasonActivationService service;
    private UUID competitionId;
    private UUID activeSeasonId;

    @BeforeEach
    void setUp() {
        service = new SeasonActivationService(DEFAULTS, competitionRepo, seasonRepo);
        competitionId = UUID.randomUUID();
        activeSeasonId = UUID.randomUUID();
    }

    @Test
    void preSeasonNotOpen_noSwitch() {
        Competition competition = buildCompetition(null);
        Season activeSeason = buildSeason(activeSeasonId, OffsetDateTime.now().plusDays(1));

        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findById(activeSeasonId)).thenReturn(Optional.of(activeSeason));

        service.checkAndActivateUpcomingSeason();

        verify(competitionRepo, never()).promoteUpcomingSeason(any(), any(), any());
    }

    @Test
    void preSeasonOpenButNoUpcomingSeasonAssigned_noSwitch() {
        Competition competition = buildCompetition(null);
        Season activeSeason = buildSeason(activeSeasonId, OffsetDateTime.now().minusDays(1));

        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findById(activeSeasonId)).thenReturn(Optional.of(activeSeason));

        service.checkAndActivateUpcomingSeason();

        verify(competitionRepo, never()).promoteUpcomingSeason(any(), any(), any());
    }

    @Test
    void preSeasonOpenAndUpcomingAssigned_promotes() {
        UUID upcomingId = UUID.randomUUID();
        Competition competition = buildCompetition(upcomingId);
        Season activeSeason = buildSeason(activeSeasonId, OffsetDateTime.now().minusDays(1));

        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findById(activeSeasonId)).thenReturn(Optional.of(activeSeason));

        service.checkAndActivateUpcomingSeason();

        verify(competitionRepo).promoteUpcomingSeason(competitionId, upcomingId, activeSeasonId);
    }

    @Test
    void alreadySwitched_idempotent() {
        // If active season is the upcoming one (already switched), isPreSeasonOpen() is false
        // because the upcoming season has no preSeasonOpensAt set
        Competition competition = buildCompetition(null);
        Season alreadySwitchedSeason = Season.builder()
                .id(activeSeasonId)
                .clientId(1)
                .competitionId(competitionId)
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(LocalDate.now().plusMonths(1))
                .endDate(LocalDate.now().plusMonths(10))
                .completed(false)
                .preSeasonOpensAt(null)
                .initialRankings(java.util.List.of())
                .build();

        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findById(activeSeasonId)).thenReturn(Optional.of(alreadySwitchedSeason));

        service.checkAndActivateUpcomingSeason();

        verify(competitionRepo, never()).promoteUpcomingSeason(any(), any(), any());
    }

    private Competition buildCompetition(UUID upcomingSeasonId) {
        return Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(activeSeasonId)
                .upcomingSeasonId(upcomingSeasonId)
                .build();
    }

    private Season buildSeason(UUID id, OffsetDateTime preSeasonOpensAt) {
        return Season.builder()
                .id(id)
                .clientId(1)
                .competitionId(competitionId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .startDate(LocalDate.now().minusMonths(9))
                .endDate(LocalDate.now())
                .completed(false)
                .preSeasonOpensAt(preSeasonOpensAt)
                .initialRankings(java.util.List.of())
                .build();
    }
}
