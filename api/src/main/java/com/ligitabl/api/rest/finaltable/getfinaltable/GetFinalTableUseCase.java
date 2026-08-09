package com.ligitabl.api.rest.finaltable.getfinaltable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ligitabl.api.config.FinalTableDevProperties;
import com.ligitabl.api.rest.finaltable.shared.FinalTableError;
import com.ligitabl.api.rest.finaltable.shared.FinalTableRowsJson;
import com.ligitabl.api.rest.finaltable.shared.FinalTableSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.shared.share.SharePredictionTextBuilder;
import com.ligitabl.api.web.shared.user.DisplayNames;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.service.FinalTableScorer;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.StandingsRepo;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

/**
 * Assembles the Final Table page for a signed-in user, or the read-only teaser for a guest.
 *
 * <p>A guest — and a signed-in user with no row yet — both see the season baseline, so the page
 * always shows a table rather than an empty state.
 */
@Component
@RequiredArgsConstructor
public class GetFinalTableUseCase {

    private final FinalTablePredictionRepo predictionRepo;
    private final FinalTableSupport finalTableSupport;
    private final TeamRepo teamRepo;
    private final SharePredictionTextBuilder shareTextBuilder;
    private final FinalTableDevProperties devProperties;
    private final FinalTableRowsJson rowsJson;
    private final CompetitionRepo competitionRepo;
    private final StandingsRepo standingsRepo;

    @Value("${ligitabl.frontend.share-url:${ligitabl.frontend.url:http://localhost:8080}}")
    private String frontendShareUrl;

    /**
     * @param userId null for a guest
     * @param displayName the viewer's own name, for the share card; null for a guest. Cleaned here
     *     rather than trusted — see {@link DisplayNames}
     */
    public Either<FinalTableError, FinalTableViewData> execute(UUID userId, String publicId, String displayName) {
        return finalTableSupport.activeSeason().map(season -> build(season, userId, publicId, displayName));
    }

    private FinalTableViewData build(Season season, UUID userId, String publicId, String displayName) {
        boolean guest = userId == null;
        FinalTablePrediction prediction = guest
                ? null
                : predictionRepo.findByUserAndSeason(userId, season.getId()).orElse(null);

        List<TeamRank> rankings = prediction != null
                ? TeamRank.inPositionOrder(prediction.getRankings())
                : finalTableSupport.baselineRankings(season);

        Map<String, Team> teamsByCode = teamRepo.findAllTeamsByCode(rankings);
        boolean revealed = prediction != null && prediction.isScored();
        boolean entryOpen = finalTableSupport.isEntryOpen(season);
        String shareUrl = publicId == null ? null : shareUrl(publicId, season);

        List<StandingsTeamRank> liveStandings = !guest && prediction != null && !entryOpen && !revealed
                ? standingsRepo.findLatestBySeason(season.getId()).map(Standings::getRankings).orElse(List.of())
                : List.of();
        boolean liveProgress = !liveStandings.isEmpty();

        return FinalTableViewData.builder()
                .rankings(rankings)
                .teamsByCode(teamsByCode)
                .rowsJson(rowsJson.rows(rankings, teamsByCode))
                .zonesJson(rowsJson.zones(rankings.size()))
                .competitionName(competitionName(season))
                .seasonLabel(seasonLabel(season))
                .teamCount(rankings.size())
                .entryOpen(entryOpen)
                .hasEntry(prediction != null)
                .revealed(revealed)
                .swaps(prediction != null ? prediction.getSwaps() : List.of())
                .swapCount(prediction != null ? prediction.getSwapCount() : 0)
                .settledAt(prediction != null ? prediction.getSettledAt() : null)
                .resultRankings(revealed ? prediction.getResultRankings() : null)
                .baseScore(revealed ? prediction.getBaseScore() : null)
                .zeroesCount(revealed ? prediction.getZeroesCount() : null)
                .bonusPoints(revealed ? prediction.getBonusPoints() : null)
                .totalScore(revealed ? prediction.getTotalScore() : null)
                .roundStatus(finalTableSupport.entryStatus(season).name())
                .shareUrl(shareUrl)
                .shareText(shareUrl == null
                        ? null
                        : shareTextBuilder.buildFinalTable(rankings, teamsByCode, shareUrl))
                .shareRowsJson(
                        rowsJson.shareRows(rankings, teamsByCode, revealed ? prediction.getResultRankings() : null))
                .isGuest(guest)
                .devPreviewEnabled(devProperties.isEnabled())
                .liveProgress(liveProgress)
                .liveRowsJson(liveProgress ? rowsJson.liveRows(rankings, teamsByCode, liveStandings) : "[]")
                .ownerName(guest ? null : DisplayNames.clean(displayName))
                .maxHitPoints(season.getMaxHitPoints())
                .maxScore(season.getMaxHitPoints() + rankings.size() * FinalTableScorer.ZERO_BONUS)
                .totalHits(revealed && prediction.getBaseScore() != null
                        ? season.getMaxHitPoints() - prediction.getBaseScore()
                        : null)
                .build();
    }

    /** Falls back to the season name so the banner never renders a blank title. */
    private String competitionName(Season season) {
        return competitionRepo
                .findById(season.getCompetitionId())
                .map(Competition::getName)
                .orElse(season.getName());
    }

    /** "2026-27" → "26/27", the form the scorecard and social copy use. */
    private String seasonLabel(Season season) {
        if (season.getSlug() == null) {
            return "";
        }
        String shorthand = season.getSlug().toShorthand();
        return shorthand.length() == 4 ? shorthand.substring(0, 2) + "/" + shorthand.substring(2) : shorthand;
    }

    private String shareUrl(String publicId, Season season) {
        // toShorthand(), not getSlug(): the latter is a SeasonSlug record, so interpolating it
        // yields "SeasonSlug[value=2026-27]" in the URL. Shorthand is also what the public route parses.
        return "%s/final-table/u/%s/%s"
                .formatted(frontendShareUrl, publicId, season.getSlug().toShorthand());
    }
}
