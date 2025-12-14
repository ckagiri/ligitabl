package com.ligitabl.api.importer;

import java.time.LocalDate;

import lombok.Data;

/**
 * Minimal DTO for the football-data.org competitions endpoint
 * (/v4/competitions/{code}). We only need the currentSeason.id in
 * order to map to Ligitabl's Season.clientId.
 */
@Data
public class ExternalCompetitionDto {

    private Integer id;
    private String name;
    private String code;
    private String emblem;

    private CurrentSeasonDto currentSeason;

    @Data
    public static class CurrentSeasonDto {
        private Integer id;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer currentMatchday;
    }
}
