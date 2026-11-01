package com.ligitabl.api.usecase.finaltable.shared;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.domain.league.StandingsTeamRank;
import com.ligitabl.model.domain.league.Team;
import com.ligitabl.model.domain.league.TeamRank;
import com.ligitabl.model.domain.prediction.ResultTeamRank;

import lombok.extern.slf4j.Slf4j;

/**
 * Serialises table rows for the Alpine component and the share canvas, for both the owner's page
 * and the public view. A swap reorders rows client-side, so each row carries code and display name.
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
     * As {@link #rows}, plus each team's position in the current standings — the locked-but-not-yet
     * revealed view, where a player can see how their table is tracking.
     */
    public String liveRows(List<TeamRank> rankings, Map<String, Team> teamsByCode, List<StandingsTeamRank> standings) {
        Map<String, Integer> positionByCode = standings == null
                ? Map.of()
                : standings.stream()
                        .collect(Collectors.toMap(
                                StandingsTeamRank::teamCode, StandingsTeamRank::position, (a, b) -> a));

        List<Map<String, Object>> rows = TeamRank.inPositionOrder(rankings).stream()
                .map(rank -> {
                    Map<String, Object> row = row(rank, teamsByCode);
                    Integer current = positionByCode.get(rank.getCode());
                    if (current != null) {
                        row.put("current", current);
                    }
                    return row;
                })
                .toList();
        return write(rows);
    }

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

    /**
     * Qualification zones as inclusive position ranges, e.g.
     * {@code {"CL":[1,5],"UEL":[6,7],"UECL":[8,8],"REL":[18,20]}}. Derived from the team count and
     * emitted as data, so the view needs no league-specific branching.
     *
     * <p>⚠️ These are the current Premier League allocations.
     */
    public String zones(int teamCount) {
        Map<String, int[]> zones = new LinkedHashMap<>();
        if (teamCount >= 12) {
            zones.put("CL", new int[] {1, 5});
            zones.put("UEL", new int[] {6, 7});
            zones.put("UECL", new int[] {8, 8});
            zones.put("REL", new int[] {teamCount - 2, teamCount});
        }
        return write(zones, "{}");
    }

    private String write(Object value) {
        return write(value, EMPTY);
    }

    /**
     * An empty literal rather than a 500, so the page still renders. The fallback must match the
     * shape the client parses: {@code []} for rows, {@code {}} for zones.
     */
    private String write(Object value, String fallback) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("[FINAL_TABLE_ROWS_JSON_FAILED] {}", e.getMessage(), e);
            return fallback;
        }
    }
}
