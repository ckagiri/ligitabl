package com.ligitabl.api.rest.match;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class MatchDto {
    UUID id;
    Integer clientId;
    UUID roundId;
    UUID homeTeamId;
    UUID awayTeamId;
    String homeTeam;
    String awayTeam;
    String name; // Generated: "arsenal-v-chelsea"
    String slug;
    String status;
    OffsetDateTime kickOff;
    String venue;
    int matchday;
    Score score;
    Integer homeScore;
    Integer awayScore;

    /**
     * UTC ISO-8601 timestamp string, intended for client-side localization via `data-timestamp`.
     * Kept out of JSON responses to avoid API shape changes.
     */
    @JsonIgnore
    public String getFormattedTime() {
        if (getKickOff() == null) {
            return null;
        }
        return getKickOff().toInstant().toString();
    }

    public static MatchDto from(Match match, Team homeTeam, Team awayTeam) {
        if (match == null) return null;

        String name = homeTeam.getSlug().value() + "-v-" + awayTeam.getSlug().value();
        Integer homeScore = match.getScore() == null ? null : match.getScore().getHomeGoals();
        Integer awayScore = match.getScore() == null ? null : match.getScore().getAwayGoals();

        return MatchDto.builder()
                .id(match.getId())
                .clientId(match.getClientId())
                .roundId(match.getRoundId())
                .homeTeamId(match.getHomeTeamId())
                .awayTeamId(match.getAwayTeamId())
                .homeTeam(homeTeam.getShortName() != null ? homeTeam.getShortName() : homeTeam.getName())
                .awayTeam(awayTeam.getShortName() != null ? awayTeam.getShortName() : awayTeam.getName())
                .name(name)
                .slug(match.getSlug())
                .status(match.getStatus() == null ? null : match.getStatus().name())
                .kickOff(match.getKickOff())
                .venue(match.getVenue())
                .matchday(match.getMatchday())
                .score(match.getScore())
                .homeScore(homeScore)
                .awayScore(awayScore)
                .build();
    }

    public static List<MatchDto> listOf(List<Match> matches, Map<UUID, Team> teamsById) {
        return matches.stream()
                .map(match -> from(match, teamsById.get(match.getHomeTeamId()), teamsById.get(match.getAwayTeamId())))
                .toList();
    }
}
