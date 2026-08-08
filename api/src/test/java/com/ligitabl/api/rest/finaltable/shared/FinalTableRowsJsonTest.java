package com.ligitabl.api.rest.finaltable.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.TeamSlug;

class FinalTableRowsJsonTest {

    private final FinalTableRowsJson rowsJson = new FinalTableRowsJson();

    private static final List<TeamRank> TABLE =
            List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("CHE", 3));

    /** Team.getCode() derives from tla, so that is what identifies a team here. */
    private static Team team(String tla, String shortName, String shorterName) {
        return Team.builder()
                .tla(tla)
                .name(tla + " FC")
                .shortName(shortName)
                .shorterName(shorterName)
                .slug(TeamSlug.of(tla.toLowerCase() + "-fc"))
                .build();
    }

    @Test
    void serialisesCodeAndDisplayNamePerRow() {
        var teams = Map.of("ARS", team("ARS", "Arsenal", "Arsenal"));

        String json = rowsJson.rows(TABLE, teams);

        assertThat(json).contains("\"code\":\"ARS\"").contains("\"name\":\"Arsenal\"");
    }

    @Test
    void carriesBothNamesSoTheRowCanSwitchOnScreenWidth() {
        // name is the fuller label (lg and up); shortName is the compact one, falling back to name
        // when a team has no shorterName.
        var teams = Map.of(
                "ARS", team("ARS", "Arsenal", "Ars"),
                "LIV", team("LIV", "Liverpool", null));

        String json = rowsJson.rows(TABLE, teams);

        assertThat(json).contains("\"name\":\"Arsenal\",\"shortName\":\"Ars\"");
        assertThat(json).contains("\"name\":\"Liverpool\",\"shortName\":\"Liverpool\"");
        // CHE has no Team at all, so the code stands in for both rather than rendering null.
        assertThat(json).contains("\"name\":\"CHE\",\"shortName\":\"CHE\"");
    }

    @Test
    void emitsRowsInPositionOrderRegardlessOfInputOrder() {
        // The client renders the array as given, so ordering cannot be left to the caller.
        List<TeamRank> shuffled = List.of(TeamRank.of("CHE", 3), TeamRank.of("ARS", 1), TeamRank.of("LIV", 2));

        String json = rowsJson.rows(shuffled, Map.of());

        assertThat(json.indexOf("ARS")).isLessThan(json.indexOf("LIV"));
        assertThat(json.indexOf("LIV")).isLessThan(json.indexOf("CHE"));
    }

    @Test
    void shareRowsOmitActualAndHitWhileUnscored() {
        String json = rowsJson.shareRows(TABLE, Map.of(), null);

        assertThat(json).doesNotContain("actual").doesNotContain("hit");
    }

    @Test
    void shareRowsCarryActualAndHitOnceScored() {
        List<ResultTeamRank> results = List.of(
                new ResultTeamRank(TeamRank.of("ARS", 1), 1, 0), new ResultTeamRank(TeamRank.of("LIV", 2), 5, 3));

        String json = rowsJson.shareRows(TABLE, Map.of(), results);

        assertThat(json).contains("\"actual\":1").contains("\"hit\":0");
        assertThat(json).contains("\"actual\":5").contains("\"hit\":3");
    }

    @Test
    void handlesEmptyAndNullInputWithoutThrowing() {
        assertThat(rowsJson.rows(List.of(), Map.of())).isEqualTo("[]");
        assertThat(rowsJson.rows(null, Map.of())).isEqualTo("[]");
        assertThat(rowsJson.rows(TABLE, null)).contains("\"name\":\"ARS\"");
    }
}
