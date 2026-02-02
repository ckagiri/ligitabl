package com.ligitabl.api.rest.match;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
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
    String name; // Generated: "arsenal-v-chelsea"
    String slug;
    MatchStatus status;
    OffsetDateTime kickOff;
    String venue;
    int matchday;
    Score score;

    public static MatchDto from(Match match, Team homeTeam, Team awayTeam) {
        if (match == null) return null;

        String name = homeTeam.getSlug().value() + "-v-" + awayTeam.getSlug().value();

        return MatchDto.builder()
                .id(match.getId())
                .clientId(match.getClientId())
                .roundId(match.getRoundId())
                .homeTeamId(match.getHomeTeamId())
                .awayTeamId(match.getAwayTeamId())
                .name(name)
                .slug(match.getSlug())
                .status(match.getStatus())
                .kickOff(match.getKickOff())
                .venue(match.getVenue())
                .matchday(match.getMatchday())
                .score(match.getScore())
                .build();
    }

    public static List<MatchDto> listOf(List<Match> matches, Map<UUID, Team> teamsById) {
        return matches.stream()
                .map(match -> from(match, teamsById.get(match.getHomeTeamId()), teamsById.get(match.getAwayTeamId())))
                .toList();
    }
}
