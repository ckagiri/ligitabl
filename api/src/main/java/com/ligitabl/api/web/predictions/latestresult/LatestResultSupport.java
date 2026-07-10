package com.ligitabl.api.web.predictions.latestresult;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LatestResultSupport {

    private final SeasonRepo seasonRepo;
    private final RoundResultRepo roundResultRepo;
    private final CompetitionDefaults competitionDefaults;

    /** The most recent RoundResult for the user in the active season, if any. */
    public Optional<RoundResult> getLatestResult(UUID userId) {
        Optional<Season> season = seasonRepo.findActiveSeason(competitionDefaults.defaultCompetitionSlug());
        if (season.isEmpty()) {
            return Optional.empty();
        }
        return roundResultRepo.findLatestByUserAndSeason(userId, season.get().getId());
    }

    public void setUserViewed(RoundResult result, boolean viewed) {
        result.setUserViewed(viewed);
        roundResultRepo.save(result);
    }
}
