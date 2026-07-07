package com.ligitabl.api.rest.contest.renamecontest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.repo.ContestRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Renames a contest. Names are unique per owner per season, enforced here and by a DB constraint. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RenameContestUseCase {

    private final ContestRepo contestRepo;

    @Transactional
    public Either<RenameContestError, RenameContestResult> execute(RenameContestCommand cmd) {
        Contest contest = contestRepo.findById(cmd.contestId()).orElse(null);
        if (contest == null) return Either.left(new RenameContestError.ContestNotFound(cmd.contestId()));

        if (!contest.isOwnedBy(cmd.userId())) return Either.left(new RenameContestError.NotOwner(cmd.contestId()));

        String trimmed = cmd.name() == null ? "" : cmd.name().trim();
        if (trimmed.isEmpty()) return Either.left(new RenameContestError.BlankName());

        if (contestRepo.existsByOwnerSeasonAndName(contest.getSeasonId(), contest.getOwnerId(), trimmed, contest.getId()))
            return Either.left(new RenameContestError.NameConflict(trimmed));

        contest.setName(trimmed);
        Contest saved = contestRepo.save(contest);

        log.info("User {} renamed contest {} to '{}'", cmd.userId(), contest.getId(), trimmed);
        return Either.right(new RenameContestResult(saved.getId(), saved.getName()));
    }
}
