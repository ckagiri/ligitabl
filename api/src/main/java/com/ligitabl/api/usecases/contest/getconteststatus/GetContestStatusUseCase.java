package com.ligitabl.api.usecases.contest.getconteststatus;

import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GetContestStatusUseCase {

    private final SeasonRepo seasonRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;

    public ContestStatus execute(UUID userId) {
        Season activeSeason = seasonRepo.findActiveSeason()
                .orElse(null);

        if (activeSeason == null) {
            return new ContestStatus(false, null, null, List.of());
        }

        Optional<SeasonPrediction> prediction = predictionRepo
            .findByUserAndSeason(userId, activeSeason.getId());

        List<Entry> userEntries = entryRepo.findByUserId(userId);

        return new ContestStatus(
                prediction.isPresent(),
                activeSeason,
                prediction.orElse(null),
                userEntries
        );
    }

}

