package com.ligitabl.api.web.predictions.userpredictions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.WhatIfPrediction;
import com.ligitabl.model.domain.WhatIfScore;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.WhatIfPredictionRepo;

@ExtendWith(MockitoExtension.class)
@DisplayName("WhatIfRecapBuilder — win/draw/loss classification")
class WhatIfRecapBuilderTest {

    @Mock
    private WhatIfPredictionRepo whatIfPredictionRepo;

    @Mock
    private MatchRepo matchRepo;

    private WhatIfRecapBuilder builder;

    private final UUID userId = UUID.randomUUID();
    private final UUID roundId = UUID.randomUUID();

    private final List<Match> matches = new ArrayList<>();
    private final List<WhatIfScore> guesses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        builder = new WhatIfRecapBuilder(whatIfPredictionRepo, matchRepo);
    }

    // ─── Win guesses: exact side, any scoreline, no leniency ──────────────────

    @Test
    @DisplayName("guessed home win + home won by any margin -> Win")
    void homeWinGuess_homeWon_isWin() {
        graded(guess(2, 1), actual(4, 0));
        assertThat(recap().winCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("guessed home win + it drew -> Loss (no one-goal leniency for win guesses)")
    void homeWinGuess_drew_isLoss() {
        graded(guess(2, 1), actual(1, 1));
        assertThat(recap().lossCount()).isEqualTo(1);
        assertThat(recap().drawCount()).isZero();
    }

    @Test
    @DisplayName("guessed home win + away won by one -> Loss, not a near-miss Draw")
    void homeWinGuess_awayWonByOne_isLoss() {
        graded(guess(2, 1), actual(1, 2));
        assertThat(recap().lossCount()).isEqualTo(1);
        assertThat(recap().drawCount()).isZero();
    }

    @Test
    @DisplayName("guessed away win + away won -> Win")
    void awayWinGuess_awayWon_isWin() {
        graded(guess(0, 1), actual(1, 3));
        assertThat(recap().winCount()).isEqualTo(1);
    }

    // ─── Draw guesses: exact -> Win, one-goal margin -> Draw, else Loss ───────

    @Test
    @DisplayName("guessed draw + it drew (any scoreline) -> Win")
    void drawGuess_drew_isWin() {
        graded(guess(1, 1), actual(3, 3));
        assertThat(recap().winCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("guessed draw + home won by exactly one -> Draw (near miss)")
    void drawGuess_homeWonByOne_isDraw() {
        graded(guess(1, 1), actual(2, 1));
        assertThat(recap().drawCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("guessed draw + away won by exactly one -> Draw (near miss)")
    void drawGuess_awayWonByOne_isDraw() {
        graded(guess(0, 0), actual(1, 2));
        assertThat(recap().drawCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("guessed draw + won by two or more -> Loss")
    void drawGuess_wonByTwo_isLoss() {
        graded(guess(1, 1), actual(3, 1));
        assertThat(recap().lossCount()).isEqualTo(1);
    }

    // ─── Aggregation, line format, and what's excluded ────────────────────────

    @Test
    @DisplayName("played equals wins + draws + losses, with lines carrying the guessed letter")
    void aggregatesBucketsAndLines() {
        graded(guess(2, 0), actual(1, 0)); // home-win guess, home won -> Win, guessed "1"
        graded(guess(1, 1), actual(2, 1)); // draw guess, one-goal margin -> Draw, guessed "X"
        graded(guess(0, 2), actual(3, 0)); // away-win guess, home won -> Loss, guessed "2"

        WhatIfRecap recap = recap();

        assertThat(recap.played()).isEqualTo(3);
        assertThat(recap.winCount()).isEqualTo(1);
        assertThat(recap.drawCount()).isEqualTo(1);
        assertThat(recap.lossCount()).isEqualTo(1);
        assertThat(recap.wins().get(0).guessedOutcome()).isEqualTo("1");
        assertThat(recap.draws().get(0).guessedOutcome()).isEqualTo("X");
        assertThat(recap.losses().get(0).guessedOutcome()).isEqualTo("2");
        assertThat(recap.wins().get(0).actualScore()).isEqualTo("1 - 0");
        assertThat(recap.wins().get(0).homeTeamCode()).isEqualTo("ARS");
        assertThat(recap.wins().get(0).awayTeamCode()).isEqualTo("CHE");

        // `all` keeps the round's fixture order and carries the grade for the per-match marks.
        assertThat(recap.all()).hasSize(3);
        assertThat(recap.all().stream().map(WhatIfRecap.Line::grade)).containsExactly("WIN", "DRAW", "LOSS");
    }

    @Test
    @DisplayName("a guessed match that never finished is skipped, not graded")
    void unfinishedMatchIsSkipped() {
        graded(guess(1, 0), actual(2, 0));
        UUID postponedId = UUID.randomUUID();
        guesses.add(new WhatIfScore(postponedId, 1, 1));
        matches.add(match(postponedId, MatchStatus.POSTPONED, null));

        assertThat(recap().played()).isEqualTo(1);
    }

    @Test
    @DisplayName("no saved what-if -> no recap")
    void noSavedPrediction_isEmpty() {
        when(whatIfPredictionRepo.findByUserAndRound(userId, roundId)).thenReturn(Optional.empty());

        assertThat(builder.build(userId, roundId)).isEmpty();
        verifyNoInteractions(matchRepo);
    }

    @Test
    @DisplayName("saved what-if but nothing gradable -> no recap")
    void nothingGradable_isEmpty() {
        UUID matchId = UUID.randomUUID();
        guesses.add(new WhatIfScore(matchId, 1, 1));
        matches.add(match(matchId, MatchStatus.POSTPONED, null));

        assertThat(build()).isEmpty();
    }

    @Test
    @DisplayName("a guest (null user) is never looked up")
    void nullUser_isEmpty() {
        assertThat(builder.build(null, roundId)).isEmpty();
        verifyNoInteractions(whatIfPredictionRepo, matchRepo);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private record Goals(int home, int away) {}

    private static Goals guess(int home, int away) {
        return new Goals(home, away);
    }

    private static Goals actual(int home, int away) {
        return new Goals(home, away);
    }

    /** Registers a guessed match plus the real, finished result it will be graded against. */
    private void graded(Goals guessed, Goals real) {
        UUID matchId = UUID.randomUUID();
        guesses.add(new WhatIfScore(matchId, guessed.home(), guessed.away()));
        matches.add(match(
                matchId,
                MatchStatus.FINISHED,
                Score.builder().homeGoals(real.home()).awayGoals(real.away()).build()));
    }

    private WhatIfRecap recap() {
        return build().orElseThrow(() -> new AssertionError("expected a recap"));
    }

    private Optional<WhatIfRecap> build() {
        when(whatIfPredictionRepo.findByUserAndRound(userId, roundId))
                .thenReturn(Optional.of(WhatIfPrediction.builder()
                        .userId(userId)
                        .roundId(roundId)
                        .scores(List.copyOf(guesses))
                        .build()));
        when(matchRepo.findByRoundIdWithTeams(roundId)).thenReturn(List.copyOf(matches));
        return builder.build(userId, roundId);
    }

    private Match match(UUID id, MatchStatus status, Score score) {
        return Match.builder()
                .id(id)
                .roundId(roundId)
                .status(status)
                .score(score)
                .homeTeam(Team.builder().tla("ARS").build())
                .awayTeam(Team.builder().tla("CHE").build())
                .build();
    }
}
