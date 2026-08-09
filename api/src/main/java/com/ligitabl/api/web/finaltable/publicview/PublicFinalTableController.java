package com.ligitabl.api.web.finaltable.publicview;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.finaltable.shared.FinalTableRowsJson;
import com.ligitabl.api.web.shared.share.SharePredictionTextBuilder;
import com.ligitabl.api.web.shared.user.DisplayNames;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.service.FinalTableScorer;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Public, no-login view of someone's Final Table: {@code /final-table/u/{publicId}/{seasonShorthand}}.
 *
 * <p>Always read-only. Carries OG/Twitter meta so the link unfurls, which is the point of a game
 * whose whole payoff is showing people what you called in August.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PublicFinalTableController {

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final UserRepo userRepo;
    private final FinalTablePredictionRepo predictionRepo;
    private final TeamRepo teamRepo;
    private final SharePredictionTextBuilder shareTextBuilder;
    private final CompetitionDefaults competitionDefaults;
    private final FinalTableRowsJson rowsJson;

    @Value("${ligitabl.frontend.share-url:${ligitabl.frontend.url:http://localhost:8080}}")
    private String frontendShareUrl;

    @GetMapping("/final-table/u/{publicId}/{seasonShorthand}")
    public String publicFinalTable(
            @PathVariable String publicId,
            @PathVariable String seasonShorthand,
            Model model,
            HttpServletResponse response) {

        Optional<Competition> competition = competitionRepo.findBySlug(competitionDefaults.defaultCompetitionSlug());
        if (competition.isEmpty()) {
            return notFound(model, response, "Competition not found");
        }

        Season season;
        try {
            season = seasonRepo
                    .findByCompetitionIdAndSlug(competition.get().getId(), SeasonSlug.fromShorthand(seasonShorthand))
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            // A malformed slug is a bad link, not a server error.
            return notFound(model, response, "Season not found");
        }
        if (season == null) {
            return notFound(model, response, "Season not found");
        }

        User user;
        try {
            user = userRepo.findByPublicId(PublicId.create(publicId)).orElse(null);
        } catch (IllegalArgumentException e) {
            return notFound(model, response, "Player not found");
        }
        if (user == null) {
            return notFound(model, response, "Player not found");
        }

        FinalTablePrediction prediction =
                predictionRepo.findByUserAndSeason(user.getId(), season.getId()).orElse(null);
        if (prediction == null) {
            return notFound(model, response, "This player has no final table for that season");
        }

        List<TeamRank> rankings = TeamRank.inPositionOrder(prediction.getRankings());
        var teamsByCode = teamRepo.findAllTeamsByCode(rankings);
        boolean revealed = prediction.isScored();
        String shareUrl = "%s/final-table/u/%s/%s"
                .formatted(frontendShareUrl, publicId, season.getSlug().toShorthand());

        // One cleaned name for every use below. User.getDisplayName() is a plain Lombok getter over
        // a nullable column, so without this a user who never set one renders as "null's Final
        // Table"; and a name containing markup would be painted verbatim onto the share canvas.
        String ownerName = DisplayNames.clean(user.getDisplayName());
        String ownerPossessive = ownerName == null ? "This player's" : DisplayNames.possessive(ownerName);

        model.addAttribute("pageTitle", "%s Final Table".formatted(ownerPossessive));
        model.addAttribute("ownerName", ownerName == null ? "This player" : ownerName);
        // Exposed so the page heading does not hardcode "'s" — Charles's vs Ann's is one rule, and
        // it already lives in DisplayNames.possessive.
        model.addAttribute("ownerPossessive", ownerPossessive);
        // Distance ceiling plus the per-club bonus — tracks the season's team count rather than
        // assuming 20 clubs and 400 points. Same formula as the owner page and the leaderboard.
        int maxScore = season.getMaxHitPoints() + rankings.size() * FinalTableScorer.ZERO_BONUS;
        model.addAttribute("maxScore", maxScore);
        model.addAttribute("maxHitPoints", season.getMaxHitPoints());
        model.addAttribute("teamCount", rankings.size());
        model.addAttribute("rankings", rankings);
        model.addAttribute("teamsByCode", teamsByCode);
        model.addAttribute("revealed", revealed);
        model.addAttribute("resultRankings", revealed ? prediction.getResultRankings() : null);
        model.addAttribute("baseScore", revealed ? prediction.getBaseScore() : null);
        model.addAttribute("zeroesCount", revealed ? prediction.getZeroesCount() : null);
        model.addAttribute("bonusPoints", revealed ? prediction.getBonusPoints() : null);
        model.addAttribute("totalScore", revealed ? prediction.getTotalScore() : null);
        model.addAttribute("swapCount", prediction.getSwapCount());
        // When they settled, for the pre-kickoff state.
        model.addAttribute("settledAt", prediction.getSettledAt());
        model.addAttribute("seasonName", season.getName());
        model.addAttribute("shareUrl", shareUrl);
        // Deliberately null, not the built text.
        model.addAttribute("shareText", null);
        model.addAttribute("rowsJson", rowsJson.rows(rankings, teamsByCode));
        model.addAttribute("zonesJson", rowsJson.zones(rankings.size()));
        model.addAttribute(
                "shareRowsJson",
                rowsJson.shareRows(rankings, teamsByCode, revealed ? prediction.getResultRankings() : null));
        model.addAttribute("shareCardTitle", "%s Final Table".formatted(ownerPossessive));
        model.addAttribute("shareCardKicker", "Final table prediction");
        // A visitor is looking at someone else's table, so "Share your table" is wrong here — it is
        // this page they would be passing on, not a table of their own.
        model.addAttribute("shareCardHeading", "Share this page");
        model.addAttribute("shareCardSubtitle", "%d clubs · shared before season kickoff".formatted(rankings.size()));
        model.addAttribute("competitionName", season.getName());
        model.addAttribute("ogTitle", "%s Final Table prediction".formatted(ownerPossessive));
        model.addAttribute(
                "ogDescription",
                revealed
                        ? "Scored %d/%d with %d exact positions."
                                .formatted(prediction.getTotalScore(), maxScore, prediction.getZeroesCount())
                        : "Locked in before a ball was kicked. Revealed at the end of the season.");
        // Read-only for everyone, including the owner: this URL is the shareable artifact.
        model.addAttribute("readOnly", true);
        model.addAttribute("entryOpen", false);
        model.addAttribute("isGuest", true);
        model.addAttribute("devPreviewEnabled", false);

        return "final-table-public";
    }

    private String notFound(Model model, HttpServletResponse response, String message) {
        log.info("GET /final-table/u — {}", message);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("pageTitle", "Not found");
        model.addAttribute("message", message);
        return "final-table-unavailable";
    }
}
