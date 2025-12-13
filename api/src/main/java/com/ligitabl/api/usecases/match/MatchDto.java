package com.ligitabl.api.usecases.match;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Score;

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
    String name;
    String slug;
    MatchStatus status;
    OffsetDateTime kickOff;
    String venue;
    int matchday;
    Score score;

    public static MatchDto from(Match match) {
        if (match == null) return null;
        return MatchDto.builder()
                .id(match.getId())
                .clientId(match.getClientId())
                .roundId(match.getRoundId())
                .homeTeamId(match.getHomeTeamId())
                .awayTeamId(match.getAwayTeamId())
                .slug(match.getSlug())
                .status(match.getStatus())
                .kickOff(match.getKickOff())
                .venue(match.getVenue())
                .matchday(match.getMatchday())
                .score(match.getScore())
                .build();
    }

    public static List<MatchDto> listOf(List<Match> matches) {
        return matches.stream().map(MatchDto::from).toList();
    }
}
