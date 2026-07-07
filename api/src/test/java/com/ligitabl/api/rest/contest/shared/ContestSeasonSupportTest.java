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

/**
 * Phases mirror a season's S1-S8 / Q1-Q4 structure, one round per sprint: S1=round1 ...
 * S8=round8, Q1=S1+S2, Q2=S3+S4, Q3=S5+S6, Q4=S7+S8.
 */
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
        phases = buildPhases();

        contest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .fromRoundPosition(1)
                .toRoundPosition(2)
                .build();

        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .clientId(1)
                .maxRounds(8)
                .build();
    }

    private static List<RoundSpan> buildPhases() {
        List<RoundSpan> sprints = List.of(
                sprint("S1", 1, 1),
                sprint("S2", 2, 2),
                sprint("S3", 3, 3),
                sprint("S4", 4, 4),
                sprint("S5", 5, 5),
                sprint("S6", 6, 6),
                sprint("S7", 7, 7),
                sprint("S8", 8, 8));
        List<RoundSpan> quarters =
                List.of(quarter("Q1", 1, 2), quarter("Q2", 3, 4), quarter("Q3", 5, 6), quarter("Q4", 7, 8));
        return java.util.stream.Stream.concat(sprints.stream(), quarters.stream())
                .toList();
    }

    private static RoundSpan sprint(String code, int from, int to) {
        return RoundSpan.builder()
                .code(code)
                .name(code)
                .type(PhaseType.SPRINT)
                .from(from)
                .to(to)
                .build();
    }

    private static RoundSpan quarter(String code, int from, int to) {
        return RoundSpan.builder()
                .code(code)
                .name(code)
                .type(PhaseType.QUARTER)
                .from(from)
                .to(to)
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

        // contest spans S1-S2 (toRoundPosition=2); its own final sprint is S2 (round 2)
        assertThat(support.isFinalSprintUnderway(contest, 1)).isFalse();
        assertThat(support.isFinalSprintUnderway(contest, 2)).isTrue();
        assertThat(support.isFinalSprintUnderway(contest, 3)).isTrue();
    }
}
