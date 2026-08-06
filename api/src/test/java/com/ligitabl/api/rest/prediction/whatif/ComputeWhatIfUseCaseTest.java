package com.ligitabl.api.rest.prediction.whatif;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.domain.StandingsCalculatorService;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;
import com.ligitabl.model.repo.TeamRepo;

@ExtendWith(MockitoExtension.class)
class ComputeWhatIfUseCaseTest {


    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private TeamRepo teamRepo;

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private StandingsRepo standingsRepo;

    private ComputeWhatIfUseCase useCase;

    private UUID seasonId;
    private UUID round1Id;
    private UUID round2Id;

    private UUID teamArsId;
    private UUID teamLivId;
    private UUID teamMciId;
    private UUID teamCheId;

    private Team teamArs;
    private Team teamLiv;
    private Team teamMci;
    private Team teamChe;

    private Season season;
    private Round round;

    private Match priorArsChe;
    private Match priorLivMci;
    private Match currentArsMci;
    private Match currentLivChe;

    @BeforeEach
    void setUp() {
        seasonId = UUID.randomUUID();
        round1Id = UUID.randomUUID();
        round2Id = UUID.randomUUID();

        teamArsId = UUID.randomUUID();
        teamLivId = UUID.randomUUID();
        teamMciId = UUID.randomUUID();
        teamCheId = UUID.randomUUID();

        teamArs = team(teamArsId, "ARS");
        teamLiv = team(teamLivId, "LIV");
        teamMci = team(teamMciId, "MCI");
        teamChe = team(teamCheId, "CHE");

        season = Season.builder()
                .id(seasonId)
                .currentRoundId(round2Id)
                .completed(false)
                .mainContestId(UUID.randomUUID())
                .maxHitPoints(8)
                .initialRankings(List.of(
                        TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3), TeamRank.of("CHE", 4)))
                .build();

        round = Round.builder()
                .id(round2Id)
                .seasonId(seasonId)
                .position(2)
                .finalized(false)
                .name("Round 2")
                .slug("round-2")
                .build();

        // Round 1 (finished): ARS 3-0 CHE, LIV 2-1 MCI
        priorArsChe = finishedMatch(round1Id, teamArsId, teamCheId, 3, 0);
        priorLivMci = finishedMatch(round1Id, teamLivId, teamMciId, 2, 1);

        // Round 2 (current, not yet played)
        currentArsMci = scheduledMatch(round2Id, teamArsId, teamMciId);
        currentLivChe = scheduledMatch(round2Id, teamLivId, teamCheId);

        useCase = new ComputeWhatIfUseCase(
                competitionDefaults,
                seasonRepo,
                roundRepo,
                matchRepo,
                teamRepo,
                new RoundSupport(roundRepo, matchRepo, hierarchyValidator, competitionDefaults),
                new StandingsCalculatorService(teamRepo, matchRepo, seasonRepo, standingsRepo),
                TestClock.FIXED);

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
    }

    /**
     * Round resolution, stubbed per-test rather than in setUp: the season-phase tests short-circuit
     * before the round is ever looked up, and a blanket stub would either go unused there (strict-stub
     * failure) or have to be made lenient, which would stop verifying it anywhere.
     */
    private void stubCurrentRound() {
        when(roundRepo.findById(round2Id)).thenReturn(Optional.of(round));
    }

    /** Stubs reached only once score validation has passed. */
    private void stubSuccessfulCalculation(List<Match> currentRoundMatchesWithTeams) {
        when(matchRepo.findByRoundIdWithTeams(round2Id)).thenReturn(currentRoundMatchesWithTeams);
        when(matchRepo.findFinishedMatchesUpToRoundWithTeams(seasonId, 2))
                .thenReturn(List.of(priorArsChe, priorLivMci));
        when(teamRepo.findAllByCodes(Set.of("ARS", "LIV", "MCI", "CHE")))
                .thenReturn(List.of(teamArs, teamLiv, teamMci, teamChe));
    }

    // ─── Season phase: pre-season and in-play only ───────────────────────────

    @Test
    void shouldAllowPreSeason_soRoundOneCanBePlayedOut() {
        stubCurrentRound();
        when(seasonRepo.findActiveSeason("premier-league"))
                .thenReturn(Optional.of(seasonInPhase(
                        OffsetDateTime.now().minusDays(1), // pre-season already opened
                        OffsetDateTime.now().plusDays(1), // predictions not yet open
                        TestClock.TODAY.plusDays(7)))); // season hasn't started
        when(matchRepo.findByRoundId(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe));
        stubSuccessfulCalculation(List.of(currentArsMci, currentLivChe));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(new WhatIfCommand(
                List.of(new WhatIfScore(currentArsMci.getId(), 2, 0), new WhatIfScore(currentLivChe.getId(), 1, 0))));

        assertTrue(result.isRight(), () -> "expected success but got " + (result.isLeft() ? result.getLeft() : ""));
    }

    @Test
    void shouldRejectOffSeason() {
        when(seasonRepo.findActiveSeason("premier-league"))
                .thenReturn(Optional.of(seasonInPhase(
                        null, // pre-season never opened
                        OffsetDateTime.now().plusDays(1),
                        TestClock.TODAY.plusDays(7))));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(new WhatIfCommand(List.of()));

        assertTrue(result.isLeft());
        assertInstanceOf(WhatIfError.SeasonNotOpen.class, result.getLeft());
    }

    @Test
    void shouldRejectSetupMode_evenWhileInPlay() {
        Season inSetup = Season.builder()
                .id(seasonId)
                .currentRoundId(round2Id)
                .completed(false)
                .mainContestId(null) // setup mode is the absence of a main contest
                .maxHitPoints(8)
                .initialRankings(List.of())
                .build();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(inSetup));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(new WhatIfCommand(List.of()));

        assertTrue(result.isLeft());
        assertInstanceOf(WhatIfError.SeasonInSetupMode.class, result.getLeft());
    }

    private Season seasonInPhase(
            OffsetDateTime preSeasonOpensAt, OffsetDateTime predictionsOpenAt, LocalDate startDate) {
        return Season.builder()
                .id(seasonId)
                .currentRoundId(round2Id)
                .completed(false)
                .mainContestId(UUID.randomUUID())
                .maxHitPoints(8)
                .preSeasonOpensAt(preSeasonOpensAt)
                .predictionsOpenAt(predictionsOpenAt)
                .startDate(startDate)
                .initialRankings(List.of(
                        TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3), TeamRank.of("CHE", 4)))
                .build();
    }

    @Test
    void shouldComputeWhatIfStandings_whenAllScoresProvided() {
        stubCurrentRound();
        when(matchRepo.findByRoundId(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe));
        stubSuccessfulCalculation(List.of(currentArsMci, currentLivChe));

        WhatIfCommand command = new WhatIfCommand(List.of(
                new WhatIfScore(currentArsMci.getId(), 2, 0), // ARS beats MCI
                new WhatIfScore(currentLivChe.getId(), 1, 0) // LIV beats CHE
                ));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(command);

        assertTrue(result.isRight(), () -> "expected success but got " + (result.isLeft() ? result.getLeft() : ""));
        List<StandingsTeamRank> standings = result.get().whatIfStandings();

        assertEquals(4, standings.size());
        assertPosition(standings, "ARS", 1, 6, 5); // 2 wins: 3-0, 2-0 => 6pts, GF5 GA0
        assertPosition(standings, "LIV", 2, 6, 2); // 2 wins: 2-1, 1-0 => 6pts, GF3 GA1
        assertPosition(standings, "MCI", 3, 0, -3); // 2 losses: 1-2, 0-2 => 0pts, GF1 GA4
        assertPosition(standings, "CHE", 4, 0, -4); // 2 losses: 0-3, 0-1 => 0pts, GF0 GA4
    }

    @Test
    void shouldExcludePostponedMatch_fromRequiredScoresAndCalculation() {
        stubCurrentRound();
        Match postponed = scheduledMatch(round2Id, teamMciId, teamCheId);
        postponed.setStatus(MatchStatus.POSTPONED);

        when(matchRepo.findByRoundId(round2Id))
                .thenReturn(List.of(currentArsMci, currentLivChe, postponed)); // still OPEN: POSTPONED is neutral
        stubSuccessfulCalculation(List.of(currentArsMci, currentLivChe, postponed));

        // Only the two scheduled matches are scored — the postponed one is not required.
        WhatIfCommand command = new WhatIfCommand(
                List.of(new WhatIfScore(currentArsMci.getId(), 2, 0), new WhatIfScore(currentLivChe.getId(), 1, 0)));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(command);

        assertTrue(result.isRight(), () -> "expected success but got " + (result.isLeft() ? result.getLeft() : ""));
        assertEquals(4, result.get().whatIfStandings().size());
    }

    @Test
    void shouldRejectPostponedMatchScore_asUnknownMatch() {
        stubCurrentRound();
        Match postponed = scheduledMatch(round2Id, teamMciId, teamCheId);
        postponed.setStatus(MatchStatus.POSTPONED);

        when(matchRepo.findByRoundId(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe, postponed));
        when(matchRepo.findByRoundIdWithTeams(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe, postponed));

        WhatIfCommand command = new WhatIfCommand(List.of(
                new WhatIfScore(currentArsMci.getId(), 2, 0),
                new WhatIfScore(currentLivChe.getId(), 1, 0),
                new WhatIfScore(postponed.getId(), 0, 0)));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertInstanceOf(WhatIfError.UnknownMatch.class, result.getLeft());
    }

    /**
     * A locked round still projects: the user can no longer change the scores, but the page has to be
     * able to show the standings behind the ones they saved while it was open. The real result of an
     * already-played match is ignored — the hypothetical score is what's replayed.
     */
    @Test
    void shouldComputeOnLockedRound_replayingTheHypotheticalOverRealResults() {
        stubCurrentRound();
        Match livChePlayed = finishedMatch(round2Id, teamLivId, teamCheId, 0, 4); // CHE thrashed LIV for real
        when(matchRepo.findByRoundId(round2Id)).thenReturn(List.of(currentArsMci, livChePlayed)); // mix -> LOCKED
        when(matchRepo.findByRoundIdWithTeams(round2Id)).thenReturn(List.of(currentArsMci, livChePlayed));
        when(matchRepo.findFinishedMatchesUpToRoundWithTeams(seasonId, 2))
                .thenReturn(List.of(priorArsChe, priorLivMci));
        when(teamRepo.findAllByCodes(Set.of("ARS", "LIV", "MCI", "CHE")))
                .thenReturn(List.of(teamArs, teamLiv, teamMci, teamChe));

        WhatIfCommand command = new WhatIfCommand(List.of(
                new WhatIfScore(currentArsMci.getId(), 2, 0),
                new WhatIfScore(livChePlayed.getId(), 1, 0))); // guessed LIV win, not the real 0-4

        Either<WhatIfError, WhatIfResult> result = useCase.execute(command);

        assertTrue(result.isRight(), () -> "expected success but got " + (result.isLeft() ? result.getLeft() : ""));
        assertFalse(result.get().roundOpen());
        // Same table as the all-open case: the guess, not the real 0-4, is what counted.
        assertPosition(result.get().whatIfStandings(), "LIV", 2, 6, 2);
        assertPosition(result.get().whatIfStandings(), "CHE", 4, 0, -4);
    }

    @Test
    void shouldReportRoundOpen_whenRoundIsStillOpen() {
        stubCurrentRound();
        when(matchRepo.findByRoundId(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe));
        stubSuccessfulCalculation(List.of(currentArsMci, currentLivChe));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(new WhatIfCommand(
                List.of(new WhatIfScore(currentArsMci.getId(), 2, 0), new WhatIfScore(currentLivChe.getId(), 1, 0))));

        assertTrue(result.isRight());
        assertTrue(result.get().roundOpen());
    }

    @Test
    void shouldRequireScoresForAlreadyPlayedMatches_onALockedRound() {
        stubCurrentRound();
        Match livChePlayed = finishedMatch(round2Id, teamLivId, teamCheId, 0, 4);
        when(matchRepo.findByRoundId(round2Id)).thenReturn(List.of(currentArsMci, livChePlayed));
        when(matchRepo.findByRoundIdWithTeams(round2Id)).thenReturn(List.of(currentArsMci, livChePlayed));

        WhatIfCommand command = new WhatIfCommand(List.of(new WhatIfScore(currentArsMci.getId(), 1, 0)));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertInstanceOf(WhatIfError.MissingScores.class, result.getLeft());
        assertEquals(List.of(livChePlayed.getId()), ((WhatIfError.MissingScores) result.getLeft()).matchIds());
    }

    @Test
    void shouldReturnMissingScores_whenNotAllScheduledMatchesScored() {
        stubCurrentRound();
        when(matchRepo.findByRoundId(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe));
        when(matchRepo.findByRoundIdWithTeams(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe));

        WhatIfCommand command = new WhatIfCommand(List.of(new WhatIfScore(currentArsMci.getId(), 1, 0)));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertInstanceOf(WhatIfError.MissingScores.class, result.getLeft());
        assertEquals(List.of(currentLivChe.getId()), ((WhatIfError.MissingScores) result.getLeft()).matchIds());
    }

    @Test
    void shouldReturnUnknownMatch_whenMatchIdNotInCurrentRound() {
        stubCurrentRound();
        when(matchRepo.findByRoundId(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe));
        when(matchRepo.findByRoundIdWithTeams(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe));

        UUID strayMatchId = UUID.randomUUID();
        WhatIfCommand command = new WhatIfCommand(List.of(
                new WhatIfScore(currentArsMci.getId(), 1, 0),
                new WhatIfScore(currentLivChe.getId(), 1, 0),
                new WhatIfScore(strayMatchId, 0, 0)));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertInstanceOf(WhatIfError.UnknownMatch.class, result.getLeft());
        assertEquals(List.of(strayMatchId), ((WhatIfError.UnknownMatch) result.getLeft()).matchIds());
    }

    @Test
    void shouldReturnInvalidScore_whenGoalsAreNegative() {
        stubCurrentRound();
        when(matchRepo.findByRoundId(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe));
        when(matchRepo.findByRoundIdWithTeams(round2Id)).thenReturn(List.of(currentArsMci, currentLivChe));

        WhatIfCommand command = new WhatIfCommand(
                List.of(new WhatIfScore(currentArsMci.getId(), -1, 0), new WhatIfScore(currentLivChe.getId(), 1, 0)));

        Either<WhatIfError, WhatIfResult> result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertInstanceOf(WhatIfError.InvalidScore.class, result.getLeft());
    }

    private void assertPosition(
            List<StandingsTeamRank> standings,
            String teamCode,
            int expectedPosition,
            int expectedPoints,
            int expectedGd) {
        StandingsTeamRank rank = standings.stream()
                .filter(s -> s.getRanking().getCode().equals(teamCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No standing found for " + teamCode));
        assertEquals(expectedPosition, rank.getRanking().getPosition(), teamCode + " position");
        assertEquals(expectedPoints, rank.getMetadata().getPoints(), teamCode + " points");
        assertEquals(expectedGd, rank.getMetadata().getGd(), teamCode + " goal difference");
    }

    private Team team(UUID id, String code) {
        return Team.builder()
                .id(id)
                .name(code)
                .shortName(code)
                .slug(TeamSlug.of(code.toLowerCase()))
                .tla(code)
                .build();
    }

    private Match finishedMatch(UUID roundId, UUID homeTeamId, UUID awayTeamId, int homeGoals, int awayGoals) {
        return Match.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .roundId(roundId)
                .homeTeamId(homeTeamId)
                .awayTeamId(awayTeamId)
                .status(MatchStatus.FINISHED)
                .score(Score.builder().homeGoals(homeGoals).awayGoals(awayGoals).build())
                .build();
    }

    private Match scheduledMatch(UUID roundId, UUID homeTeamId, UUID awayTeamId) {
        return Match.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .roundId(roundId)
                .homeTeamId(homeTeamId)
                .awayTeamId(awayTeamId)
                .status(MatchStatus.SCHEDULED)
                .build();
    }
}
