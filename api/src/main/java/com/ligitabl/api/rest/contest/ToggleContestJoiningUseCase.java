package com.ligitabl.api.rest.contest;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.repo.ContestRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToggleContestJoiningUseCase {

    private final ContestRepo contestRepo;

    public sealed interface Error {
        record ContestNotFound(UUID contestId) implements Error {}
        record NotOwner(UUID userId, UUID contestId) implements Error {}
    }

    public record Result(UUID contestId, boolean isOpen) {}

    @Transactional
    public Either<Error, Result> execute(UUID contestId, UUID userId) {
        var contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) {
            return Either.left(new Error.ContestNotFound(contestId));
        }

        if (!contest.isOwnedBy(userId)) {
            return Either.left(new Error.NotOwner(userId, contestId));
        }

        contest.setOpen(!contest.isOpen());
        contestRepo.save(contest);

        log.info("User {} toggled joining for contest {} — isOpen={}", userId, contestId, contest.isOpen());
        return Either.right(new Result(contestId, contest.isOpen()));
    }
}
