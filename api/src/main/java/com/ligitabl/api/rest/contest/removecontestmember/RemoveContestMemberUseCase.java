package com.ligitabl.api.rest.contest.removecontestmember;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemoveContestMemberUseCase {

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final ContestSeasonSupport contestSeasonSupport;

    public sealed interface Error {
        record ContestNotFound(UUID contestId) implements Error {}

        record NotOwner(UUID requesterId, UUID contestId) implements Error {}

        record CannotRemoveOwner(UUID contestId) implements Error {}

        record NotAMember(UUID targetUserId, UUID contestId) implements Error {}

        /** The contest belongs to a past (no longer active) season — historical, read-only. */
        record PastSeasonContest(UUID contestId) implements Error {}

        /** The contest's own join window has closed — membership is locked. */
        record JoinWindowClosed(UUID contestId) implements Error {}
    }

    public record Result(boolean shouldSuggestCodeRegen) {}

    @Transactional
    public Either<Error, Result> execute(
            UUID contestId, UUID requesterId, UUID targetUserId, int currentRoundPosition) {
        Contest contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) {
            return Either.left(new Error.ContestNotFound(contestId));
        }

        if (!contest.isOwnedBy(requesterId)) {
            return Either.left(new Error.NotOwner(requesterId, contestId));
        }

        if (contest.isOwnedBy(targetUserId)) {
            return Either.left(new Error.CannotRemoveOwner(contestId));
        }

        var entry = entryRepo.findByUserAndContest(targetUserId, contestId).orElse(null);
        if (entry == null || entry.getRemovedAtRound() != null) {
            return Either.left(new Error.NotAMember(targetUserId, contestId));
        }

        var seasonGate = contestSeasonSupport.resolveSeasonGateStatus(contest);
        if (seasonGate.isPastSeason()) {
            return Either.left(new Error.PastSeasonContest(contestId));
        }

        if (seasonGate.isJoinWindowClosed()) {
            return Either.left(new Error.JoinWindowClosed(contestId));
        }

        if (entryRepo.hasAnyScore(targetUserId, contestId)) {
            entryRepo.softRemove(targetUserId, contestId, currentRoundPosition);
            log.info("Requester {} soft-removed member {} from contest {}", requesterId, targetUserId, contestId);
        } else {
            entryRepo.deleteByUserAndContest(targetUserId, contestId);
            log.info("Requester {} hard-deleted member {} from contest {}", requesterId, targetUserId, contestId);
        }

        return Either.right(new Result(true));
    }
}
