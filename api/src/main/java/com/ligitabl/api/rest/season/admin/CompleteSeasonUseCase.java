package com.ligitabl.api.rest.season.admin;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompleteSeasonUseCase {

    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final CompetitionDefaults competitionDefaults;

    public sealed interface Result permits Result.Ok, Result.SeasonNotFound, Result.SeasonNotEligible {
        record Ok() implements Result {}

        record SeasonNotFound() implements Result {}

        record SeasonNotEligible(String reason) implements Result {}
    }

    @Transactional
    public Result execute() {
        var season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        if (season == null) {
            return new Result.SeasonNotFound();
        }

        var currentRound = roundRepo.findById(season.getCurrentRoundId()).orElse(null);
        if (currentRound == null) {
            return new Result.SeasonNotEligible("Current round not found");
        }

        if (currentRound.getPosition() != season.getMaxRounds()) {
            return new Result.SeasonNotEligible("Current round is not the season's last round");
        }
        if (!currentRound.isFinalized() || !currentRound.isAdvanced()) {
            return new Result.SeasonNotEligible("Last round must be finalized and advanced first");
        }

        season.setCompleted(true);
        season.setCompletedAt(OffsetDateTime.now());
        seasonRepo.save(season);

        log.info("[ADMIN_COMPLETE_SEASON] season={} marked completed", season.getId());
        return new Result.Ok();
    }
}
