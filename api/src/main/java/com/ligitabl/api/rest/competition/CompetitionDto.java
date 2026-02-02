package com.ligitabl.api.rest.competition;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.Competition;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CompetitionDto {
    UUID id;
    String name;
    String slug;
    String code;

    public static CompetitionDto from(Competition competition) {
        if (competition == null) return null;
        return CompetitionDto.builder()
                .id(competition.getId())
                .name(competition.getName())
                .slug(competition.getSlug().value())
                .code(competition.getCode())
                .build();
    }

    public static List<CompetitionDto> listOf(List<Competition> competitions) {
        return competitions.stream().map(CompetitionDto::from).toList();
    }
}
