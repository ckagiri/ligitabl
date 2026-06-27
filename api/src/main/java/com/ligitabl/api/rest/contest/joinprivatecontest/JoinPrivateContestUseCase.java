package com.ligitabl.api.rest.contest.joinprivatecontest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoinPrivateContestUseCase {

    private static final int MAX_PRIVATE_CONTESTS = 20;

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final CompetitionRepo competitionRepo;
    private final MatchRepo matchRepo;

    @Transactional
    public Either<JoinPrivateContestError, JoinPrivateContestResult> execute(JoinPrivateContestCommand cmd) {
        var contest = contestRepo.findByJoinCode(cmd.joinCode()).orElse(null);
        if (contest == null) {
            return Either.left(new JoinPrivateContestError.ContestNotFound(cmd.joinCode()));
        }

        if (!contest.isOpen()) {
            return Either.left(new JoinPrivateContestError.ContestClosed());
        }

        // Verify user has made a prediction (is participating in the season)
        var season = seasonRepo.findById(contest.getSeasonId()).orElse(null);
        if (season == null || !predictionRepo.existsByUserAndSeason(cmd.userId(), contest.getSeasonId())) {
            return Either.left(new JoinPrivateContestError.NoPrediction());
        }

        var currentRound = roundRepo.findById(season.getCurrentRoundId()).orElse(null);
        if (currentRound == null) {
            return Either.left(new JoinPrivateContestError.CurrentRoundNotFound());
        }

        var competition = competitionRepo.findById(season.getCompetitionId()).orElse(null);
        if (competition == null) {
            return Either.left(new JoinPrivateContestError.CompetitionNotFound());
        }

        // Join window: contest must not have ended or passed the opening of its last sprint
        if (isJoinWindowClosed(contest.getToRoundPosition(), currentRound, competition)) {
            return Either.left(new JoinPrivateContestError.JoinWindowClosed());
        }

        // Check if user already has an entry (active or removed)
        var existingEntry = entryRepo.findByUserAndContest(cmd.userId(), contest.getId()).orElse(null);

        if (existingEntry != null && existingEntry.getRemovedAtRound() == null) {
            return Either.left(new JoinPrivateContestError.AlreadyMember());
        }

        // Member cap check (only for new joins, not re-joins of removed members)
        if (existingEntry == null && contest.getMaxEntries() != null) {
            int activeCount = entryRepo.countActiveByContestId(contest.getId());
            if (activeCount >= contest.getMaxEntries()) {
                return Either.left(new JoinPrivateContestError.MemberCapReached(contest.getMaxEntries()));
            }
        }

        // Check user's total private contest count
        if (existingEntry == null) {
            int userContestCount = contestRepo.findPrivateByUserId(cmd.userId()).size();
            if (userContestCount >= MAX_PRIVATE_CONTESTS) {
                return Either.left(new JoinPrivateContestError.MaxContestsReached(MAX_PRIVATE_CONTESTS));
            }
        }

        // Re-join or new join — save() upsert handles both paths
        Entry entry = Entry.builder()
                .userId(cmd.userId())
                .contestId(contest.getId())
                .joinedAtRound(currentRound.getPosition())
                .build();

        Entry saved = entryRepo.save(entry);
        log.info("User {} joined contest {} at round {}", cmd.userId(), contest.getId(), currentRound.getPosition());

        return Either.right(new JoinPrivateContestResult(contest.getId(), saved.getId()));
    }

    /**
     * Rules (contest from=30, to=38; last sprint from=35, to=38):
     *   pos 29  → open    (before contest starts — early join allowed)
     *   pos 34  → open    (before last sprint)
     *   pos 35 + OPEN     → open   (opening round of last sprint, still joinable)
     *   pos 35 + not OPEN → closed (opening round locked)
     *   pos 36+           → closed (past opening round of last sprint, regardless of status)
     *   pos 39+           → closed (past contest end)
     */
    private boolean isJoinWindowClosed(int toRoundPosition,
            Round currentRound, Competition competition) {
        int pos = currentRound.getPosition();

        if (pos > toRoundPosition) return true;

        if (competition.getPhases() == null) return false;

        RoundSpan endSprint = competition.getPhases().stream()
                .filter(p -> p.getType() == PhaseType.SPRINT)
                .filter(p -> p.getFrom() <= toRoundPosition && toRoundPosition <= p.getTo())
                .findFirst()
                .orElse(null);

        if (endSprint == null) return false;

        // Before the last sprint starts → still joinable
        if (pos < endSprint.getFrom()) return false;

        // Past the opening round of the last sprint → always closed
        if (pos > endSprint.getFrom()) return true;

        // Exactly at the opening round of the last sprint → open only if round is OPEN
        var matches = matchRepo.findByRoundId(currentRound.getId());
        RoundStatus status = currentRound.isFinalized()
                ? RoundStatus.FINALIZED
                : currentRound.computeStatus(matches);
        return status != RoundStatus.OPEN;
    }
}
