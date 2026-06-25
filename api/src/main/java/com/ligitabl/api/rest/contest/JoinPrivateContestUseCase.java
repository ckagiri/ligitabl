package com.ligitabl.api.rest.contest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
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

        // Join window: contest must not have ended
        if (contest.getToRoundPosition() < currentRound.getPosition()) {
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
}
