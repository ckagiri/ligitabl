package com.ligitabl.api.web.shared.season;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ligitabl.api.web.shared.share.SharePredictionTextBuilder;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

/**
 * Small shared facade over the season/round/prediction repos, for callers (public prediction
 * sharing, profile share widget) that only ever need "the active season", "its current round",
 * and "this user's prediction for it" — rather than each wiring up all three repos directly.
 */
@Component
@RequiredArgsConstructor
public class SeasonPredictionSupport {
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final TeamRepo teamRepo;
    private final SharePredictionTextBuilder sharePredictionTextBuilder;

    @Value("${ligitabl.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    public Optional<Season> findActiveSeason(String competitionSlug) {
        return seasonRepo.findActiveSeason(competitionSlug);
    }

    public Optional<Season> findSeasonById(UUID seasonId) {
        return seasonRepo.findById(seasonId);
    }

    public Optional<Round> findCurrentRound(Season season) {
        return roundRepo.findById(season.getCurrentRoundId());
    }

    public Optional<SeasonPrediction> findPrediction(UUID userId, UUID seasonId) {
        return seasonPredictionRepo.findByUserAndSeason(userId, seasonId);
    }

    public boolean hasSeasonPrediction(UUID userId, String competitionSlug) {
        return findActiveSeason(competitionSlug)
                .map(season -> findPrediction(userId, season.getId()).isPresent())
                .orElse(false);
    }

    /**
     * Prediction sharing data for the profile settings widget — visible only when the user has
     * something to share: pre-season registration (initialRankings) or an in-play prediction
     * (currentRankings).
     */
    public record ShareData(boolean visible, String shareUrl, String shareText) {
        public static ShareData hidden() {
            return new ShareData(false, null, null);
        }
    }

    public ShareData buildShareData(User user, String competitionSlug) {
        Optional<Season> seasonOpt = findActiveSeason(competitionSlug);
        if (seasonOpt.isEmpty()) {
            return ShareData.hidden();
        }
        Season season = seasonOpt.get();

        Optional<SeasonPrediction> predictionOpt = findPrediction(user.getId(), season.getId());
        if (predictionOpt.isEmpty()) {
            return ShareData.hidden();
        }
        SeasonPrediction prediction = predictionOpt.get();

        List<TeamRank> rankings;
        if (season.isPreSeason() && prediction.getInitialRankings() != null) {
            rankings = prediction.getInitialRankings();
        } else if (season.isInPlay() && prediction.getCurrentRankings() != null) {
            rankings = prediction.getCurrentRankings();
        } else {
            return ShareData.hidden();
        }

        Optional<Round> currentRoundOpt = findCurrentRound(season);
        if (currentRoundOpt.isEmpty()) {
            return ShareData.hidden();
        }
        int round = currentRoundOpt.get().getPosition();

        String shareUrl = frontendUrl + "/u/" + user.getPublicId().value() + "/"
                + season.getSlug().toShorthand() + "/gw/" + round;
        Map<String, Team> teamsByCode = teamRepo.findAllTeamsByCode(rankings);

        String shareText = sharePredictionTextBuilder.build(round, rankings, teamsByCode, shareUrl);
        return new ShareData(true, shareUrl, shareText);
    }
}
