package com.ligitabl.api.rest.contest.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

/** Uses the real Premier League phase structure (see {@link CompetitionPhaseFixtures}). */
@ExtendWith(MockitoExtension.class)
class ContestSeasonSupportTest {

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    CompetitionRepo competitionRepo;

    private ContestSeasonSupport support;

    private UUID seasonId;
    private UUID competitionId;
    private Contest contest;
    private Season season;
    private List<RoundSpan> phases;

    @BeforeEach
    void setUp() {
        support = new ContestSeasonSupport(seasonRepo, competitionRepo);

        seasonId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        phases = CompetitionPhaseFixtures.phases();

        contest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .fromRoundPosition(1)
                .toRoundPosition(9) // S1+S2 (GW1-9)
                .build();

        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .clientId(1)
                .maxRounds(38)
                .build();
    }

    @Test
    void isPastSeason_activeSeasonMatches_false() {
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));

        assertThat(support.isPastSeason(contest)).isFalse();
    }

    @Test
    void isPastSeason_differentActiveSeason_true() {
        Season activeSeason = Season.builder()
                .id(UUID.randomUUID())
                .competitionId(competitionId)
                .build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));

        assertThat(support.isPastSeason(contest)).isTrue();
    }

    @Test
    void isPastSeason_noActiveSeason_true() {
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.empty());

        assertThat(support.isPastSeason(contest)).isTrue();
    }

    @Test
    void isFinalSprintUnderway_multiSprintContest_trueOnceInOwnLastSprint() {
        Competition competition =
                Competition.builder().id(competitionId).phases(phases).build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        // contest spans S1-S2 (toRoundPosition=9); its own final sprint is S2 (GW5-9)
        assertThat(support.isFinalSprintUnderway(contest, 1)).isFalse();
        assertThat(support.isFinalSprintUnderway(contest, 5)).isTrue();
        assertThat(support.isFinalSprintUnderway(contest, 9)).isTrue();
    }
}
