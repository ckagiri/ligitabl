package com.ligitabl.api.rest.contest.deletecontest;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.Unit;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.LeaderboardRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteContestUseCase {

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final LeaderboardRepo leaderboardRepo;

    public sealed interface Error {
        record ContestNotFound(UUID contestId) implements Error {}

        record NotOwner(UUID userId, UUID contestId) implements Error {}

        record DeleteBlocked(UUID contestId) implements Error {}
    }

    @Transactional
    public Either<Error, Unit> execute(UUID contestId, UUID userId) {
        var contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) {
            return Either.left(new Error.ContestNotFound(contestId));
        }

        if (!contest.isOwnedBy(userId)) {
            return Either.left(new Error.NotOwner(userId, contestId));
        }

        int activeMembers = entryRepo.countActiveByContestId(contestId);
        if (activeMembers >= 2) {
            Integer effectiveTo = leaderboardRepo.resolveEffectiveToRound(
                    contest.getSeasonId(), contest.getFromRoundPosition(), contest.getToRoundPosition());
            if (effectiveTo != null) {
                return Either.left(new Error.DeleteBlocked(contestId));
            }
        }

        entryRepo.deleteByContestId(contestId);
        contestRepo.delete(contestId);

        log.info("User {} deleted contest {}", userId, contestId);
        return Either.right(Unit.INSTANCE);
    }
}
