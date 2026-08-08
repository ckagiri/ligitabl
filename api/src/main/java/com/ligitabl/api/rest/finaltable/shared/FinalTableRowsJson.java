package com.ligitabl.api.rest.finaltable.shared;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.StandingsTeamRank;
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
     * As {@link #rows}, plus each team's position in the current standings — the locked-but-not-yet
     * revealed view, where a player can see how their table is tracking.
     *
     * <p>Emits {@code current} only; the distance and the ↑/↓ arrow are derived client-side, the
     * same way {@code getPositionChange} already derives movement against the saved order. Nothing
     * resembling a score is serialised, and deliberately no aggregate: a total, an exact-hit count
     * or a mean distance would each be the provisional score in disguise, and the rule for this
     * view is that the app does not do that arithmetic for the player.
     *
     * <p>⚠️ Not for the share card. {@link #shareRows} publishes to anyone holding the public link;
     * provisional standings comparisons are for the owner's own page only.
     *
     * @param standings current standings; a team absent from them simply omits {@code current}
     *     rather than failing, so a partial standings row degrades to "no reading yet" per team
     */
    public String liveRows(
            List<TeamRank> rankings, Map<String, Team> teamsByCode, List<StandingsTeamRank> standings) {
        Map<String, Integer> positionByCode = standings == null
                ? Map.of()
                : standings.stream()
                        .collect(Collectors.toMap(StandingsTeamRank::teamCode, StandingsTeamRank::position, (a, b) -> a));

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

    /**
     * Qualification zones as inclusive position ranges, e.g.
     * {@code {"CL":[1,5],"UEL":[6,7],"UECL":[8,8],"REL":[18,20]}}.
     *
     * <p>Derived from the team count rather than hardcoded to 20, and emitted as data so the view
     * needs no league-specific branching. A table too small for a zone simply omits it — better than
     * colouring half a five-team league as Champions League places.
     *
     * <p>⚠️ These are the current Premier League allocations. They are display-only — nothing about
     * scoring reads them — but they will be wrong for another competition, so this is the one place
     * to fix when a second league is added.
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
     * An empty literal rather than a 500: the page still renders, the table just comes up empty (or
     * unbanded). The fallback must match the shape the client parses — {@code []} for rows,
     * {@code {}} for zones.
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
