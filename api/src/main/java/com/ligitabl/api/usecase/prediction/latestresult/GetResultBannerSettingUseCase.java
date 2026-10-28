package com.ligitabl.api.usecase.prediction.latestresult;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

/** State of the profile setting that shows the latest-result banner, read from the user's latest result. */
@Service
@RequiredArgsConstructor
public class GetResultBannerSettingUseCase {

    private final SeasonRepo seasonRepo;
    private final RoundResultRepo roundResultRepo;
    private final CompetitionDefaults competitionDefaults;

    public GetResultBannerSettingResult execute(UUID userId) {
        Optional<RoundResult> latest = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .flatMap(season -> roundResultRepo.findLatestByUserAndSeason(userId, season.getId()));
        return new GetResultBannerSettingResult(
                latest.isPresent(), latest.map(r -> !r.isUserViewed()).orElse(false));
    }
}
