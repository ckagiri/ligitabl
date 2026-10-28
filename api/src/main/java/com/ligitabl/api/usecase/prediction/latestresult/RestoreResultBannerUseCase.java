package com.ligitabl.api.usecase.prediction.latestresult;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

/** Undoes {@link DismissResultBannerUseCase} for the user's latest result, if they dismissed it. */
@Service
@RequiredArgsConstructor
public class RestoreResultBannerUseCase {

    private final SeasonRepo seasonRepo;
    private final RoundResultRepo roundResultRepo;
    private final CompetitionDefaults competitionDefaults;

    public void execute(UUID userId) {
        seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .flatMap(season -> roundResultRepo.findLatestByUserAndSeason(userId, season.getId()))
                .filter(RoundResult::isUserViewed)
                .ifPresent(result -> {
                    result.setUserViewed(false);
                    roundResultRepo.save(result);
                });
    }
}
