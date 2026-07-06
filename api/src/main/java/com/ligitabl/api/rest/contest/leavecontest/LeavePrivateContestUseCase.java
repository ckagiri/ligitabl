package com.ligitabl.api.rest.contest.leavecontest;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.Unit;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeavePrivateContestUseCase {

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final ContestSeasonSupport contestSeasonSupport;

    public sealed interface Error {
        record ContestNotFound(UUID contestId) implements Error {}

        record OwnerCannotLeave(UUID contestId) implements Error {}

        record NotAMember(UUID userId, UUID contestId) implements Error {}

        /** The contest belongs to a past (no longer active) season — historical, read-only. */
        record PastSeasonContest(UUID contestId) implements Error {}

        /** The contest's own final sprint is underway — membership is locked until it ends. */
        record FinalSprintUnderway(UUID contestId) implements Error {}
    }

    @Transactional
    public Either<Error, Unit> execute(UUID contestId, UUID userId, int currentRoundPosition) {
        Contest contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) {
            return Either.left(new Error.ContestNotFound(contestId));
        }

        if (contest.isOwnedBy(userId)) {
            return Either.left(new Error.OwnerCannotLeave(contestId));
        }

        var entry = entryRepo.findByUserAndContest(userId, contestId).orElse(null);
        if (entry == null || entry.getRemovedAtRound() != null) {
            return Either.left(new Error.NotAMember(userId, contestId));
        }

        if (contestSeasonSupport.isPastSeason(contest)) {
            return Either.left(new Error.PastSeasonContest(contestId));
        }

        if (contestSeasonSupport.isFinalSprintUnderway(contest, currentRoundPosition)) {
            return Either.left(new Error.FinalSprintUnderway(contestId));
        }

        if (entryRepo.hasAnyScore(userId, contestId)) {
            entryRepo.softRemove(userId, contestId, currentRoundPosition);
            log.info("User {} soft-removed self from contest {}", userId, contestId);
        } else {
            entryRepo.deleteByUserAndContest(userId, contestId);
            log.info("User {} hard-deleted self from contest {}", userId, contestId);
        }

        return Either.right(Unit.INSTANCE);
    }
}
