package com.ligitabl.api.usecase.contest.regeneratecontestcode;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.contest.shared.ContestSeasonSupport;
import com.ligitabl.model.domain.contest.ContestCodeGenerator;
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

    @Transactional
    public Either<RegenerateContestCodeError, RegenerateContestCodeResult> execute(UUID contestId, UUID userId) {
        var contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) {
            return Either.left(new RegenerateContestCodeError.ContestNotFound(contestId));
        }

        if (!contest.isOwnedBy(userId)) {
            return Either.left(new RegenerateContestCodeError.NotOwner(userId, contestId));
        }

        if (contestSeasonSupport.isPastSeason(contest)) {
            return Either.left(new RegenerateContestCodeError.PastSeasonContest(contestId));
        }

        String newCode = generateUniqueCode();
        contest.replaceJoinCode(newCode);
        contestRepo.save(contest);

        log.info("User {} regenerated code for contest {} — new code={}", userId, contestId, newCode);
        return Either.right(new RegenerateContestCodeResult(contestId, newCode));
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = codeGenerator.generate();
        } while (contestRepo.findByJoinCode(code).isPresent());
        return code;
    }
}
