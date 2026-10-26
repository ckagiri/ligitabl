package com.ligitabl.api.usecase.contest.togglecontestjoining;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.contest.shared.ContestSeasonSupport;
import com.ligitabl.model.repo.ContestRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToggleContestJoiningUseCase {

    private final ContestRepo contestRepo;
    private final ContestSeasonSupport contestSeasonSupport;

    @Transactional
    public Either<ToggleContestJoiningError, ToggleContestJoiningResult> execute(UUID contestId, UUID userId) {
        var contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) {
            return Either.left(new ToggleContestJoiningError.ContestNotFound(contestId));
        }

        if (!contest.isOwnedBy(userId)) {
            return Either.left(new ToggleContestJoiningError.NotOwner(userId, contestId));
        }

        if (contestSeasonSupport.isPastSeason(contest)) {
            return Either.left(new ToggleContestJoiningError.PastSeasonContest(contestId));
        }

        contest.setOpen(!contest.isOpen());
        contestRepo.save(contest);

        log.info("User {} toggled joining for contest {} — isOpen={}", userId, contestId, contest.isOpen());
        return Either.right(new ToggleContestJoiningResult(contestId, contest.isOpen()));
    }
}
