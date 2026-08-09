package com.ligitabl.api.web.shared.share;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;

/**
 * Builds the shareable social-media text for a user's prediction table — short enough to post
 * (first 5 + last 3 teams, per product spec) with a link back to the public read-only view.
 */
@Component
public class SharePredictionTextBuilder {
    private static final int HEAD_COUNT = 5;
    private static final int TAIL_COUNT = 3;

    public String build(int round, List<TeamRank> rankings, Map<String, Team> teamsByCode, String shareUrl) {
        return build(round, rankings, teamsByCode, shareUrl, false);
    }

    public String build(
            int round, List<TeamRank> rankings, Map<String, Team> teamsByCode, String shareUrl, boolean preSeason) {
        var sorted = TeamRank.inPositionOrder(rankings).stream().toList();

        StringBuilder text = new StringBuilder();
        text.append("🏆 My ")
                .append(preSeason ? "early " : "")
                .append("Gameweek ")
                .append(round)
                .append(" Prediction:\n\n");
        appendTeamLines(text, sorted, teamsByCode);
        text.append("\nCheck it out on LigiPredictor!\n").append(displayUrl(shareUrl));
        return text.toString();
    }

    /**
     * The Final Table side game's share text. Same shape and truncation as the gameweek version, but
     * framed as a season-long call rather than a round — this game has no gameweek to name.
     */
    public String buildFinalTable(List<TeamRank> rankings, Map<String, Team> teamsByCode, String shareUrl) {
        var sorted = TeamRank.inPositionOrder(rankings).stream().toList();

        StringBuilder text = new StringBuilder();
        text.append("🔮 My Final Table prediction:\n\n");
        appendTeamLines(text, sorted, teamsByCode);
        text.append("\nShared before season kickoff on LigiPredictor!\n").append(displayUrl(shareUrl));
        return text.toString();
    }

    private void appendTeamLines(StringBuilder text, List<TeamRank> sorted, Map<String, Team> teamsByCode) {
        int total = sorted.size();

        if (total <= HEAD_COUNT + TAIL_COUNT) {
            sorted.forEach(rank -> appendLine(text, rank, teamsByCode));
            return;
        }

        sorted.subList(0, HEAD_COUNT).forEach(rank -> appendLine(text, rank, teamsByCode));
        text.append("...\n");
        sorted.subList(total - TAIL_COUNT, total).forEach(rank -> appendLine(text, rank, teamsByCode));
    }

    private void appendLine(StringBuilder text, TeamRank rank, Map<String, Team> teamsByCode) {
        Team team = teamsByCode.get(rank.getCode());
        String name = team != null ? displayName(team) : rank.getCode();
        text.append(rank.getPosition()).append(' ').append(name).append('\n');
    }

    private String displayName(Team team) {
        return team.getShorterName() != null ? team.getShorterName() : team.getShortName();
    }

    private String displayUrl(String shareUrl) {
        return shareUrl.replaceFirst("^https?://", "");
    }
}
