package com.ligitabl.api.usecases.leaderboard;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use Case: Get Leaderboard
 *
 * Retrieves user contest leaderboard for the default competition's main contest.
 * Supports filtering by phase (Q1, Q2, Q3, Q4, FS).
 *
 * Flow:
 * 1. Find default competition (premier-league)
 * 2. Find active season
 * 3. Find contest (default is main)
 * 4. Resolve phase to round range
 * 5. Compute leaderboard from repository
 * 6. Return rankings with phase info
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetLeaderboardUseCase {
    private final LeaderboardRepo leaderboardRepo;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final ContestRepo contestRepo;
    private final CompetitionDefaults competitionDefaults;

    public Either<GetLeaderboardError, GetLeaderboardResult> execute(GetLeaderboardQuery query) {
        return findDefaultCompetition().flatMap(this::findActiveSeason).flatMap(seasonContext -> findMainContest(
                        seasonContext)
                .flatMap(contestContext -> resolvePhase(contestContext, query.phase())
                        .flatMap(phaseContext -> computeLeaderboard(phaseContext))));
    }

    private Either<GetLeaderboardError, Competition> findDefaultCompetition() {
        return competitionRepo
                .findBySlug(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<GetLeaderboardError, Competition>right)
                .orElse(Either.left(new GetLeaderboardError.DefaultCompetitionNotFound()));
    }

    private Either<GetLeaderboardError, SeasonContext> findActiveSeason(Competition competition) {
        return seasonRepo
                .findActiveSeason(competition.getId())
                .map(season -> Either.<GetLeaderboardError, SeasonContext>right(new SeasonContext(competition, season)))
                .orElse(Either.left(new GetLeaderboardError.ActiveSeasonNotFound()));
    }

    private Either<GetLeaderboardError, ContestContext> findMainContest(SeasonContext ctx) {
        return contestRepo
                .findMainBySeasonId(ctx.season().getId())
                .map(contest -> Either.<GetLeaderboardError, ContestContext>right(
                        new ContestContext(ctx.competition(), ctx.season(), contest)))
                .orElse(Either.left(new GetLeaderboardError.MainContestNotFound()));
    }

    private Either<GetLeaderboardError, PhaseContext> resolvePhase(ContestContext ctx, String phaseCode) {
        var code = phaseCode != null ? phaseCode : "FS";

        if (ctx.competition().getPhases() == null
                || ctx.competition().getPhases().isEmpty()) {
            return Either.left(new GetLeaderboardError.PhasesNotConfigured());
        }

        return ctx.competition().getPhases().stream()
                .filter(phase -> phase.getCode().equalsIgnoreCase(code))
                .findFirst()
                .map(phase -> Either.<GetLeaderboardError, PhaseContext>right(
                        new PhaseContext(ctx.competition(), ctx.season(), ctx.contest(), phase)))
                .orElse(Either.left(new GetLeaderboardError.InvalidPhase(code)));
    }

    private Either<GetLeaderboardError, GetLeaderboardResult> computeLeaderboard(PhaseContext ctx) {
        var rankings = leaderboardRepo.computeLeaderboard(
                ctx.contest().getId(),
                ctx.season().getId(),
                ctx.phase().getFrom(),
                ctx.phase().getTo());

        return Either.right(new GetLeaderboardResult(ctx.contest().getId(), ctx.phase(), rankings));
    }

    // Context records to avoid parameter explosion
    private record SeasonContext(Competition competition, Season season) {}

    private record ContestContext(Competition competition, Season season, Contest contest) {}

    private record PhaseContext(Competition competition, Season season, Contest contest, RoundSpan phase) {}
}
