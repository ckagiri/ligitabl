package com.ligitabl.api.web.admin;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Team;

/**
 * Normalized bootstrap payload for the admin matches page. Matches reference teams and
 * rounds by id; the client composes the lookup graph and does all filtering, sorting,
 * and pagination in the browser.
 */
public record AdminMatchesData(
        int currentRound, List<TeamEntry> teams, List<RoundEntry> rounds, List<MatchEntry> matches) {

    public record TeamEntry(UUID id, String shorterName, String code) {

        public static TeamEntry from(Team team) {
            String displayName = team.getShorterName() != null ? team.getShorterName() : team.getShortName();
            return new TeamEntry(team.getId(), displayName != null ? displayName : team.getName(), team.getCode());
        }
    }

    public record RoundEntry(UUID id, int position, String name) {

        public static RoundEntry from(Round round) {
            return new RoundEntry(round.getId(), round.getPosition(), round.getName());
        }
    }

    public record ScoreEntry(int homeGoals, int awayGoals) {

        static ScoreEntry from(Score score) {
            return score == null ? null : new ScoreEntry(score.getHomeGoals(), score.getAwayGoals());
        }
    }

    /**
     * Also serialized into the HX-Trigger header by match admin mutations so the
     * client store can patch a single match in place without refetching.
     */
    public record MatchEntry(
            UUID id,
            String slug,
            UUID homeTeamId,
            UUID awayTeamId,
            UUID roundId,
            int matchday,
            String status,
            String kickOff,
            String venue,
            ScoreEntry score) {

        public static MatchEntry from(Match match) {
            return new MatchEntry(
                    match.getId(),
                    match.getSlug(),
                    match.getHomeTeamId(),
                    match.getAwayTeamId(),
                    match.getRoundId(),
                    match.getMatchday(),
                    match.getStatus() != null ? match.getStatus().name() : null,
                    match.getKickOff() != null ? match.getKickOff().toInstant().toString() : null,
                    match.getVenue(),
                    ScoreEntry.from(match.getScore()));
        }
    }
}
