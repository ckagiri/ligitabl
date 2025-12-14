package com.ligitabl.api.importer;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Minimal DTO view of the football-data.org matches response
 * for the /v4/competitions/{code}/matches endpoint.
 */
@Data
public class ExternalMatchDto {

    @Data
    public static class MatchesResponse {
        private CompetitionDTO competition;
        private List<ExternalMatchDto> matches;
    }

    @Data
    public static class CompetitionDTO {
        private Integer id;
        private String code;
        private String name;
        private CurrentSeasonDTO currentSeason;
    }

    @Data
    public static class CurrentSeasonDTO {
        private Integer id;
        private String startDate;
        private String endDate;
    }

    @Data
    public static class TeamDTO {
        private Integer id;
        private String name;
        private String shortName;
        private String tla;
    }

    private Integer id;
    private OffsetDateTime utcDate;
    private String status;
    private Integer matchday;
    private TeamDTO homeTeam;
    private TeamDTO awayTeam;
}
