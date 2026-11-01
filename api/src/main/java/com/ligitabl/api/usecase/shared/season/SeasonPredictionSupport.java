package com.ligitabl.api.usecase.shared.season;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ligitabl.api.usecase.shared.share.SharePredictionTextBuilder;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.league.Team;
import com.ligitabl.model.domain.league.TeamRank;
import com.ligitabl.model.domain.prediction.SeasonPrediction;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

/**
 * The active season, its current round, and a user's prediction for it, for callers (public
 * sharing, the profile share widget) that need nothing more.
 */
@Component
@RequiredArgsConstructor
public class SeasonPredictionSupport {
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final TeamRepo teamRepo;
    private final SharePredictionTextBuilder sharePredictionTextBuilder;
    private final Clock clock;

    @Value("${ligitabl.frontend.share-url:${ligitabl.frontend.url:http://localhost:8080}}")
    private String frontendShareUrl;

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

    /** Visible only with something to share: a pre-season registration or an in-play prediction. */
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
        Instant now = clock.instant();
        if (season.isPreSeason(now) && prediction.getInitialRankings() != null) {
            rankings = prediction.getInitialRankings();
        } else if (season.isInPlay(now) && prediction.getCurrentRankings() != null) {
            rankings = prediction.getCurrentRankings();
        } else {
            return ShareData.hidden();
        }

        Optional<Round> currentRoundOpt = findCurrentRound(season);
        if (currentRoundOpt.isEmpty()) {
            return ShareData.hidden();
        }
        int round = currentRoundOpt.get().getPosition();

        String shareUrl = frontendShareUrl + "/u/" + user.getPublicId().value() + "/"
                + season.getSlug().toShorthand() + "/gw/" + round;
        Map<String, Team> teamsByCode = teamRepo.findAllTeamsByCode(rankings);

        String shareText =
                sharePredictionTextBuilder.build(round, rankings, teamsByCode, shareUrl, season.isPreSeason(now));
        return new ShareData(true, shareUrl, shareText);
    }
}
