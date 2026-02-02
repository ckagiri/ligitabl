package com.ligitabl.api.rest.standings;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.Team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class StandingsEntryDto {
    int position;

    // Team information
    UUID teamId;
    String teamName;
    String teamShortName;
    String teamSlug;
    String teamTla;

    // Statistics
    int played;
    int won;
    int drawn;
    int lost;
    int goalsFor;
    int goalsAgainst;
    int goalDifference;
    int points;

    public static StandingsEntryDto from(StandingsTeamRank rank, Map<String, Team> teamsByCode) {
        Team team = teamsByCode.get(rank.teamCode());

        if (team == null) {
            // Edge case: team not found in DB - use code as fallback
            return StandingsEntryDto.builder()
                    .position(rank.position())
                    .teamId(null)
                    .teamName(rank.teamCode())
                    .teamShortName(rank.teamCode())
                    .teamSlug(rank.teamCode())
                    .teamTla(rank.teamCode())
                    .played(rank.getMetadata().getPlayed())
                    .won(rank.getMetadata().getWon())
                    .drawn(rank.getMetadata().getDrawn())
                    .lost(rank.getMetadata().getLost())
                    .goalsFor(rank.getMetadata().getGf())
                    .goalsAgainst(rank.getMetadata().getGa())
                    .goalDifference(rank.getMetadata().getGd())
                    .points(rank.getMetadata().getPoints())
                    .build();
        }

        return StandingsEntryDto.builder()
                .position(rank.position())
                .teamId(team.getId())
                .teamName(team.getName())
                .teamShortName(team.getShortName())
                .teamSlug(team.getSlug().value())
                .teamTla(team.getTla())
                .played(rank.getMetadata().getPlayed())
                .won(rank.getMetadata().getWon())
                .drawn(rank.getMetadata().getDrawn())
                .lost(rank.getMetadata().getLost())
                .goalsFor(rank.getMetadata().getGf())
                .goalsAgainst(rank.getMetadata().getGa())
                .goalDifference(rank.getMetadata().getGd())
                .points(rank.getMetadata().getPoints())
                .build();
    }

    public static List<StandingsEntryDto> listOf(Standings standings, Map<String, Team> teamsByCode) {
        if (standings == null || standings.getRankings() == null) {
            return List.of();
        }

        return standings.getRankings().stream()
                .map(rank -> from(rank, teamsByCode))
                .toList();
    }
}
