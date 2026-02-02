package com.ligitabl.api.rest.round;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.Round;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class RoundDto {
    UUID id;
    UUID seasonId;
    String name;
    String slug;
    int position;

    public static RoundDto from(Round round) {
        if (round == null) return null;
        return RoundDto.builder()
                .id(round.getId())
                .seasonId(round.getSeasonId())
                .name(round.getName())
                .slug(round.getSlug())
                .position(round.getPosition())
                .build();
    }

    public static List<RoundDto> listOf(List<Round> rounds) {
        return rounds.stream().map(RoundDto::from).toList();
    }
}
