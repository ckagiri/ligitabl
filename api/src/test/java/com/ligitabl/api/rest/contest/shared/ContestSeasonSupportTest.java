package com.ligitabl.api.rest.contest.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.rest.round.shared.RoundSupport;
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

    @Mock
    RoundSupport roundSupport;

    private ContestSeasonSupport support;

    private UUID seasonId;
    private UUID competitionId;
    private Contest contest;
    private Season season;
    private List<RoundSpan> phases;

    @BeforeEach
    void setUp() {
        support = new ContestSeasonSupport(seasonRepo, competitionRepo, roundSupport);

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

    private Round round(int position) {
        return Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .position(position)
                .build();
    }

    @Test
    void isJoinWindowClosed_multiSprintContest_falseBeforeOwnLastSprintStarts() {
        Competition competition =
                Competition.builder().id(competitionId).phases(phases).build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(roundSupport.resolveCurrentRound(season)).thenReturn(round(1));

        // contest spans S1-S2 (toRoundPosition=9); its own final sprint is S2 (GW5-9)
        assertThat(support.isJoinWindowClosed(contest)).isFalse();
    }

    @Test
    void isJoinWindowClosed_multiSprintContest_trueOncePastOwnLastSprintOpeningRound() {
        Competition competition =
                Competition.builder().id(competitionId).phases(phases).build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(roundSupport.resolveCurrentRound(season)).thenReturn(round(9));

        // Past S2's own opening round (5) — closed regardless of that round's status.
        assertThat(support.isJoinWindowClosed(contest)).isTrue();
    }

    @Test
    void isJoinWindowClosed_singleSprintContest_staysOpenWhileOpeningRoundIsOpen() {
        // Regression: a contest whose entire window is one sprint (its final sprint IS its own
        // opening round) must not be locked out from round 1 — only once that round stops being
        // OPEN, matching the same boundary joining itself uses (ContestJoinWindow).
        Contest singleSprintContest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("One Shot")
                .isPrivate(true)
                .isOpen(true)
                .fromRoundPosition(10)
                .toRoundPosition(14) // S3 only
                .build();

        Competition competition =
                Competition.builder().id(competitionId).phases(phases).build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Round openingRound = round(10);
        when(roundSupport.resolveCurrentRound(season)).thenReturn(openingRound);
        when(roundSupport.resolveStatus(openingRound)).thenReturn(RoundStatus.OPEN);

        assertThat(support.isJoinWindowClosed(singleSprintContest)).isFalse();
    }

    @Test
    void isJoinWindowClosed_singleSprintContest_closesOnceOpeningRoundLocks() {
        Contest singleSprintContest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("One Shot")
                .isPrivate(true)
                .isOpen(true)
                .fromRoundPosition(10)
                .toRoundPosition(14)
                .build();

        Competition competition =
                Competition.builder().id(competitionId).phases(phases).build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Round openingRound = round(10);
        when(roundSupport.resolveCurrentRound(season)).thenReturn(openingRound);
        when(roundSupport.resolveStatus(openingRound)).thenReturn(RoundStatus.LOCKED);

        assertThat(support.isJoinWindowClosed(singleSprintContest)).isTrue();
    }

    // ---- resolveSeasonGateStatus (combines both checks from a single season lookup) ----

    @Test
    void resolveSeasonGateStatus_pastSeason_shortCircuitsJoinWindowCheck() {
        Season activeSeason = Season.builder()
                .id(UUID.randomUUID())
                .competitionId(competitionId)
                .build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));

        var status = support.resolveSeasonGateStatus(contest);

        assertThat(status.isPastSeason()).isTrue();
        assertThat(status.isJoinWindowClosed()).isFalse();
        // Past-season short-circuit: no need to resolve competition/round at all.
        verifyNoInteractions(competitionRepo, roundSupport);
    }

    @Test
    void resolveSeasonGateStatus_currentSeason_reflectsJoinWindowState() {
        Competition competition =
                Competition.builder().id(competitionId).phases(phases).build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(roundSupport.resolveCurrentRound(season)).thenReturn(round(1));

        var status = support.resolveSeasonGateStatus(contest);

        assertThat(status.isPastSeason()).isFalse();
        assertThat(status.isJoinWindowClosed()).isFalse();
    }
}
