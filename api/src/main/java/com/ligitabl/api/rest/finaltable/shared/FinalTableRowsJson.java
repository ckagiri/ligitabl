package com.ligitabl.api.rest.finaltable.shared;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;

import lombok.extern.slf4j.Slf4j;

/**
 * Serialises table rows for the Alpine component and the share canvas.
 *
 * <p>Shared between the owner's page and the public view so both serialise identically — a swap
 * reorders rows client-side, so the browser needs each row's code and display name without another
 * round trip.
 */
@Component
@Slf4j
public class FinalTableRowsJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EMPTY = "[]";

    /** Code and display name per row, in position order. */
    public String rows(List<TeamRank> rankings, Map<String, Team> teamsByCode) {
        List<Map<String, Object>> rows = TeamRank.inPositionOrder(rankings).stream()
                .map(rank -> row(rank, teamsByCode))
                .toList();
        return write(rows);
    }

    /**
     * As {@link #rows}, plus actual position and distance once the row is scored — so the share card
     * shows the comparison rather than just the prediction.
     */
    public String shareRows(
            List<TeamRank> rankings, Map<String, Team> teamsByCode, List<ResultTeamRank> resultRankings) {
        Map<String, ResultTeamRank> byCode = resultRankings == null
                ? Map.of()
                : resultRankings.stream()
                        .collect(Collectors.toMap(r -> r.getRanking().getCode(), r -> r, (a, b) -> a));

        List<Map<String, Object>> rows = TeamRank.inPositionOrder(rankings).stream()
                .map(rank -> {
                    Map<String, Object> row = row(rank, teamsByCode);
                    ResultTeamRank result = byCode.get(rank.getCode());
                    if (result != null) {
                        row.put("actual", result.getStandingsPosition());
                        row.put("hit", result.getHit());
                    }
                    return row;
                })
                .toList();
        return write(rows);
    }

    /**
     * Both names travel: the row shows the compact one on small screens and the fuller one from
     * {@code lg} up, mirroring the main prediction table.
     */
    private Map<String, Object> row(TeamRank rank, Map<String, Team> teamsByCode) {
        Team team = teamsByCode == null ? null : teamsByCode.get(rank.getCode());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", rank.getCode());
        row.put("name", fullName(rank.getCode(), team));
        row.put("shortName", compactName(rank.getCode(), team));
        return row;
    }

    private static String fullName(String code, Team team) {
        if (team == null) {
            return code;
        }
        return team.getShortName() != null ? team.getShortName() : team.getName();
    }

    private static String compactName(String code, Team team) {
        if (team == null) {
            return code;
        }
        return team.getShorterName() != null ? team.getShorterName() : fullName(code, team);
    }

    /** An empty array rather than a 500: the page still renders, the table just comes up empty. */
    private String write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("[FINAL_TABLE_ROWS_JSON_FAILED] {}", e.getMessage(), e);
            return EMPTY;
        }
    }
}
