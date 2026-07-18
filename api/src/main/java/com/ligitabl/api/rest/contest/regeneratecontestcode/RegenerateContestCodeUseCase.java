package com.ligitabl.api.rest.contest.regeneratecontestcode;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.service.ContestCodeGenerator;
import com.ligitabl.model.repo.ContestRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegenerateContestCodeUseCase {

    private final ContestRepo contestRepo;
    private final ContestCodeGenerator codeGenerator;
    private final ContestSeasonSupport contestSeasonSupport;

    public sealed interface Error {
        record ContestNotFound(UUID contestId) implements Error {}

        record NotOwner(UUID userId, UUID contestId) implements Error {}

        /** The contest belongs to a past (no longer active) season — historical, read-only. */
        record PastSeasonContest(UUID contestId) implements Error {}
    }

    public record Result(UUID contestId, String joinCode) {}

    @Transactional
    public Either<Error, Result> execute(UUID contestId, UUID userId) {
        var contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) {
            return Either.left(new Error.ContestNotFound(contestId));
        }

        if (!contest.isOwnedBy(userId)) {
            return Either.left(new Error.NotOwner(userId, contestId));
        }

        if (contestSeasonSupport.isPastSeason(contest)) {
            return Either.left(new Error.PastSeasonContest(contestId));
        }

        String newCode = generateUniqueCode();
        contest.setJoinCode(newCode);
        contestRepo.save(contest);

        log.info("User {} regenerated code for contest {} — new code={}", userId, contestId, newCode);
        return Either.right(new Result(contestId, newCode));
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = codeGenerator.generate();
        } while (contestRepo.findByJoinCode(code).isPresent());
        return code;
    }
}
