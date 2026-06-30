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

    @Mock CompetitionRepo competitionRepo;
    @Mock SeasonRepo seasonRepo;

    private SeasonActivationService service;
    private UUID competitionId;
    private UUID activeSeasonId;
    private Competition competition;

    @BeforeEach
    void setUp() {
        service = new SeasonActivationService(DEFAULTS, competitionRepo, seasonRepo);
        competitionId = UUID.randomUUID();
        activeSeasonId = UUID.randomUUID();
        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(activeSeasonId)
                .build();
    }

    @Test
    void preSeasonNotOpen_noSwitch() {
        Season activeSeason = buildSeason(activeSeasonId, OffsetDateTime.now().plusDays(1));

        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findById(activeSeasonId)).thenReturn(Optional.of(activeSeason));

        service.checkAndActivateUpcomingSeason();

        verify(competitionRepo, never()).updateActiveSeasonId(any(), any());
    }

    @Test
    void preSeasonOpenButNoUpcomingSeason_noSwitch() {
        Season activeSeason = buildSeason(activeSeasonId, OffsetDateTime.now().minusDays(1));

        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findById(activeSeasonId)).thenReturn(Optional.of(activeSeason));
        when(seasonRepo.findUpcomingSeason(competitionId, activeSeasonId)).thenReturn(Optional.empty());

        service.checkAndActivateUpcomingSeason();

        verify(competitionRepo, never()).updateActiveSeasonId(any(), any());
    }

    @Test
    void preSeasonOpenAndUpcomingExists_switchesActiveSeasonId() {
        Season activeSeason = buildSeason(activeSeasonId, OffsetDateTime.now().minusDays(1));
        UUID upcomingId = UUID.randomUUID();
        Season upcomingSeason = Season.builder()
                .id(upcomingId)
                .clientId(1)
                .competitionId(competitionId)
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(LocalDate.now().plusMonths(1))
                .endDate(LocalDate.now().plusMonths(10))
                .completed(false)
                .initialRankings(java.util.List.of())
                .build();

        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findById(activeSeasonId)).thenReturn(Optional.of(activeSeason));
        when(seasonRepo.findUpcomingSeason(competitionId, activeSeasonId)).thenReturn(Optional.of(upcomingSeason));

        service.checkAndActivateUpcomingSeason();

        verify(competitionRepo).updateActiveSeasonId(competitionId, upcomingId);
    }

    @Test
    void alreadySwitched_idempotent() {
        // If active season is the upcoming one (already switched), isPreSeasonOpen() is false
        // because the upcoming season has no preSeasonOpensAt set
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

        verify(competitionRepo, never()).updateActiveSeasonId(any(), any());
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
