package com.ligitabl.api.rest.leaderboard.getleaderboard;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use Case: Get Leaderboard
 * <p>
 * Retrieves user contest leaderboard for the default competition's main contest.
 * Supports filtering by phase (Q1, Q2, Q3, Q4, FS).
 * <p>
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
    private final RoundRepo roundRepo;
    private final CompetitionDefaults competitionDefaults;

    public Either<GetLeaderboardError, GetLeaderboardResult> execute(GetLeaderboardQuery query) {
        var competition = competitionRepo
                .findBySlug(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        if (competition == null) {
            return Either.left(new GetLeaderboardError.DefaultCompetitionNotFound());
        }

        if (competition.getPhases() == null || competition.getPhases().isEmpty()) {
            return Either.left(new GetLeaderboardError.PhasesNotConfigured());
        }

        var season = seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        if (season == null) {
            return Either.left(new GetLeaderboardError.ActiveSeasonNotFound());
        }

        var contest = contestRepo.findMainBySeasonId(season.getId()).orElse(null);
        if (contest == null) {
            return Either.left(new GetLeaderboardError.MainContestNotFound());
        }

        var phase = resolvePhase(competition, query.phase(), season);
        if (phase == null) {
            return Either.left(new GetLeaderboardError.InvalidPhase(query.phase()));
        }

        int offset = query.offset() != null ? query.offset() : 0;
        int limit = query.limit() != null ? query.limit() : 100;

        var response = leaderboardRepo.computeLeaderboard(
                contest.getId(), season.getId(), phase.getFrom(), phase.getTo(), query.userId(), offset, limit);

        return Either.right(new GetLeaderboardResult(
                contest.getId(),
                phase,
                response.effectiveToRound(),
                response.entries(),
                competition.getPhases(),
                response.userEntry(),
                response.userInCurrentPage(),
                response.userPageOffset(),
                response.totalParticipants(),
                response.hasNext(),
                response.hasPrevious(),
                offset,
                limit));
    }

    private RoundSpan resolvePhase(Competition competition, String phaseCode, Season season) {
        if (competition.getPhases() == null) {
            return null;
        }

        // Explicit phase requested — look it up directly
        if (phaseCode != null) {
            return competition.getPhases().stream()
                    .filter(phase -> phase.getCode().equalsIgnoreCase(phaseCode))
                    .findFirst()
                    .orElse(null);
        }

        // No phase specified — dynamically resolve current quarter
        if (season.getCurrentRoundId() != null) {
            var currentRound = roundRepo.findById(season.getCurrentRoundId()).orElse(null);
            if (currentRound != null) {
                int roundPos = currentRound.getPosition();
                int effectiveRoundPos = (!currentRound.isFinalized() && roundPos > 1) ? (roundPos - 1) : roundPos;
                var quarter = competition.getPhases().stream()
                        .filter(p -> p.getCode().startsWith("Q"))
                        .filter(p -> effectiveRoundPos >= p.getFrom() && effectiveRoundPos <= p.getTo())
                        .findFirst()
                        .orElse(null);
                if (quarter != null) {
                    return quarter;
                }
            }
        }

        // Fallback to FS if quarter detection fails
        return competition.getPhases().stream()
                .filter(phase -> phase.getCode().equalsIgnoreCase("FS"))
                .findFirst()
                .orElse(null);
    }
}
