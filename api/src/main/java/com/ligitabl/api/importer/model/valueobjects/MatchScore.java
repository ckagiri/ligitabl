package com.ligitabl.api.importer.model.valueobjects;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

import java.util.Optional;

import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.errors.ValidationError;
import com.ligitabl.api.shared.Either;

public record MatchScore(int homeGoals, int awayGoals) {

    public static Either<ImportError, Optional<MatchScore>> of(Integer homeGoals, Integer awayGoals) {
        if (homeGoals == null && awayGoals == null) {
            return right(Optional.empty());
        }

        if (homeGoals == null || awayGoals == null) {
            return left(ValidationError.of("Score must include both home and away goals when present", "score"));
        }

        if (homeGoals < 0 || awayGoals < 0) {
            return left(ValidationError.of("Goals cannot be negative", "score"));
        }

        return right(Optional.of(new MatchScore(homeGoals, awayGoals)));
    }
}
