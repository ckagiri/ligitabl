package com.ligitabl.api.usecase.contest.removecontestmember;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.contest.shared.ContestSeasonSupport;
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

    @Transactional
    public Either<RemoveContestMemberError, RemoveContestMemberResult> execute(
            UUID contestId, UUID requesterId, UUID targetUserId, int currentRoundPosition) {
        Contest contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) {
            return Either.left(new RemoveContestMemberError.ContestNotFound(contestId));
        }

        if (!contest.isOwnedBy(requesterId)) {
            return Either.left(new RemoveContestMemberError.NotOwner(requesterId, contestId));
        }

        if (contest.isOwnedBy(targetUserId)) {
            return Either.left(new RemoveContestMemberError.CannotRemoveOwner(contestId));
        }

        var entry = entryRepo.findByUserAndContest(targetUserId, contestId).orElse(null);
        if (entry == null || entry.getRemovedAtRound() != null) {
            return Either.left(new RemoveContestMemberError.NotAMember(targetUserId, contestId));
        }

        var seasonGate = contestSeasonSupport.resolveSeasonGateStatus(contest);
        if (seasonGate.isPastSeason()) {
            return Either.left(new RemoveContestMemberError.PastSeasonContest(contestId));
        }

        if (seasonGate.isJoinWindowClosed()) {
            return Either.left(new RemoveContestMemberError.JoinWindowClosed(contestId));
        }

        if (entryRepo.hasAnyScore(targetUserId, contestId)) {
            entryRepo.softRemove(targetUserId, contestId, currentRoundPosition);
            log.info("Requester {} soft-removed member {} from contest {}", requesterId, targetUserId, contestId);
        } else {
            entryRepo.deleteByUserAndContest(targetUserId, contestId);
            log.info("Requester {} hard-deleted member {} from contest {}", requesterId, targetUserId, contestId);
        }

        return Either.right(new RemoveContestMemberResult(true));
    }
}
