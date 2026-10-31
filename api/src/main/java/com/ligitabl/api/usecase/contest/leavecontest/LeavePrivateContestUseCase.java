package com.ligitabl.api.usecase.contest.leavecontest;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.Unit;
import com.ligitabl.api.usecase.contest.shared.ContestSeasonSupport;
import com.ligitabl.model.domain.contest.Contest;
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

    @Transactional
    public Either<LeavePrivateContestError, Unit> execute(UUID contestId, UUID userId, int currentRoundPosition) {
        Contest contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) {
            return Either.left(new LeavePrivateContestError.ContestNotFound(contestId));
        }

        if (contest.isOwnedBy(userId)) {
            return Either.left(new LeavePrivateContestError.OwnerCannotLeave(contestId));
        }

        var entry = entryRepo.findByUserAndContest(userId, contestId).orElse(null);
        if (entry == null || entry.getRemovedAtRound() != null) {
            return Either.left(new LeavePrivateContestError.NotAMember(userId, contestId));
        }

        var seasonGate = contestSeasonSupport.resolveSeasonGateStatus(contest);
        if (seasonGate.isPastSeason()) {
            return Either.left(new LeavePrivateContestError.PastSeasonContest(contestId));
        }

        if (seasonGate.isJoinWindowClosed()) {
            return Either.left(new LeavePrivateContestError.JoinWindowClosed(contestId));
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
