package com.ligitabl.api.web.predictions.latestresult;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.repo.RoundResultRepo;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class DismissResultBannerUseCase {
    private final RoundResultRepo roundResultRepo;

    public Either<UseCaseError, Void> execute(UUID userId, int round) {
        var roundResult = roundResultRepo.findByUserAndRound(userId, round);

        if (roundResult.isEmpty()) {
            return Either.left(new NotFoundError("RoundResult", "round", round));
        }

        var result = roundResult.get();
        result.setUserViewed(true);
        roundResultRepo.save(result);

        return Either.right(null);
    }
}
