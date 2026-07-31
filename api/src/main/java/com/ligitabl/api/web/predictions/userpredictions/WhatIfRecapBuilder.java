package com.ligitabl.api.web.predictions.userpredictions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.WhatIfScore;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.WhatIfPredictionRepo;

import lombok.RequiredArgsConstructor;

/**
 * Grades a user's saved what-if guesses for a round against what actually happened.
 *
 * <p>The rule:
 * <ul>
 *   <li>Guessed a <b>win</b> (home or away) → <b>Win</b> only if that same side actually won, any
 *       scoreline. Anything else is a flat <b>Loss</b>; margin never rescues a wrong win-guess.</li>
 *   <li>Guessed a <b>draw</b> → <b>Win</b> if it actually drew; <b>Draw</b> (near-miss) if either
 *       side won by <i>exactly one</i> goal; <b>Loss</b> if the margin was two or more.</li>
 * </ul>
 * The one-goal leniency only ever applies coming from a draw guess.
 */
@Component
@RequiredArgsConstructor
public class WhatIfRecapBuilder {

    private final WhatIfPredictionRepo whatIfPredictionRepo;
    private final MatchRepo matchRepo;

    private enum Outcome {
        HOME,
        DRAW,
        AWAY
    }

    /** Empty unless the user saved a what-if for this round and at least one guess can be graded. */
    public Optional<WhatIfRecap> build(UUID userId, UUID roundId) {
        if (userId == null || roundId == null) {
            return Optional.empty();
        }

        List<WhatIfScore> guesses = whatIfPredictionRepo
                .findByUserAndRound(userId, roundId)
                .map(prediction -> prediction.getScores())
                .orElseGet(List::of);
        if (guesses.isEmpty()) {
            return Optional.empty();
        }

        Map<UUID, WhatIfScore> guessesByMatch =
                guesses.stream().collect(Collectors.toMap(WhatIfScore::matchId, Function.identity(), (a, b) -> a));

        List<WhatIfRecap.Line> all = new ArrayList<>();
        List<WhatIfRecap.Line> wins = new ArrayList<>();
        List<WhatIfRecap.Line> draws = new ArrayList<>();
        List<WhatIfRecap.Line> losses = new ArrayList<>();

        // Driven by the round's own match order (not the saved guess order) so the full list reads
        // as the fixture list the user entered scores against.
        for (Match match : matchRepo.findByRoundIdWithTeams(roundId)) {
            WhatIfScore guess = guessesByMatch.get(match.getId());
            if (guess == null || match.getStatus() != MatchStatus.FINISHED || match.getScore() == null) {
                continue; // not guessed, or nothing real to grade the guess against
            }

            Score actual = match.getScore();
            Grade grade = grade(guess, actual);
            WhatIfRecap.Line line = new WhatIfRecap.Line(
                    match.getHomeTeam().getCode(),
                    match.getAwayTeam().getCode(),
                    displayName(match.getHomeTeam()),
                    displayName(match.getAwayTeam()),
                    actual.getHomeGoals() + " - " + actual.getAwayGoals(),
                    outcomeLetter(outcomeOf(guess.homeGoals(), guess.awayGoals())),
                    grade.name());

            all.add(line);
            switch (grade) {
                case WIN -> wins.add(line);
                case DRAW -> draws.add(line);
                case LOSS -> losses.add(line);
            }
        }

        if (all.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new WhatIfRecap(
                all.size(), List.copyOf(all), List.copyOf(wins), List.copyOf(draws), List.copyOf(losses)));
    }

    private enum Grade {
        WIN,
        DRAW,
        LOSS
    }

    private Grade grade(WhatIfScore guess, Score actual) {
        Outcome guessed = outcomeOf(guess.homeGoals(), guess.awayGoals());
        Outcome happened = outcomeOf(actual.getHomeGoals(), actual.getAwayGoals());

        if (guessed != Outcome.DRAW) {
            return guessed == happened ? Grade.WIN : Grade.LOSS;
        }
        if (happened == Outcome.DRAW) {
            return Grade.WIN;
        }
        int margin = Math.abs(actual.getHomeGoals() - actual.getAwayGoals());
        return margin == 1 ? Grade.DRAW : Grade.LOSS;
    }

    /** Same shorter-name-with-fallback rule the rest of the UI uses (see {@code TeamRankDto}). */
    private String displayName(Team team) {
        return team.getShorterName() != null ? team.getShorterName() : team.getShortName();
    }

    private Outcome outcomeOf(int homeGoals, int awayGoals) {
        if (homeGoals > awayGoals) {
            return Outcome.HOME;
        }
        return homeGoals < awayGoals ? Outcome.AWAY : Outcome.DRAW;
    }

    private String outcomeLetter(Outcome outcome) {
        return switch (outcome) {
            case HOME -> "1";
            case DRAW -> "X";
            case AWAY -> "2";
        };
    }
}
