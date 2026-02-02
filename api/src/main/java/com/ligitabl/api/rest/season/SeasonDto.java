package com.ligitabl.api.rest.season;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.TeamRank;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SeasonDto {
    UUID id;
    UUID competitionId;
    String name;
    String slug;
    LocalDate startDate;
    LocalDate endDate;
    int maxRounds;
    List<TeamRank> initialRankings;

    public static SeasonDto from(Season season) {
        if (season == null) return null;
        return SeasonDto.builder()
                .id(season.getId())
                .competitionId(season.getCompetitionId())
                .name(season.getName())
                .slug(season.getSlug().value())
                .startDate(season.getStartDate())
                .endDate(season.getEndDate())
                .maxRounds(season.getMaxRounds())
                .initialRankings(season.getInitialRankings())
                .build();
    }

    public static List<SeasonDto> listOf(List<Season> seasons) {
        return seasons.stream().map(SeasonDto::from).toList();
    }
}
