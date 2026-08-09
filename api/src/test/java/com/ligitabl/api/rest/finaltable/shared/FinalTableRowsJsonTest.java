package com.ligitabl.api.rest.finaltable.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
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
    void derivesRelegationFromTheTeamCountRatherThanAssumingTwenty() {
        // An 18-team league relegates 16-18, not 18-20.
        assertThat(rowsJson.zones(20)).contains("\"REL\":[18,20]");
        assertThat(rowsJson.zones(18)).contains("\"REL\":[16,18]");
    }

    @Test
    void omitsZonesForATableTooSmallToHaveThem() {
        // Better an unbanded table than colouring half a tiny league as Champions League places.
        assertThat(rowsJson.zones(4)).isEqualTo("{}");
        assertThat(rowsJson.zones(0)).isEqualTo("{}");
    }

    @Test
    void zonesAreInclusivePositionRanges() {
        String json = rowsJson.zones(20);

        assertThat(json).contains("\"CL\":[1,5]");
        assertThat(json).contains("\"UEL\":[6,7]");
        assertThat(json).contains("\"UECL\":[8,8]");
    }

    @Test
    void handlesEmptyAndNullInputWithoutThrowing() {
        assertThat(rowsJson.rows(List.of(), Map.of())).isEqualTo("[]");
        assertThat(rowsJson.rows(null, Map.of())).isEqualTo("[]");
        assertThat(rowsJson.rows(TABLE, null)).contains("\"name\":\"ARS\"");
    }

    // --- liveRows: the locked-but-unscored view ------------------------------------------

    private static StandingsTeamRank standing(String code, int position) {
        return StandingsTeamRank.builder()
                .ranking(TeamRank.of(code, position))
                .metadata(StandingsMetadata.builder().build())
                .build();
    }

    @Test
    void liveRowsCarryEachTeamsCurrentPosition() {
        String json = rowsJson.liveRows(
                TABLE, Map.of(), List.of(standing("ARS", 2), standing("LIV", 1), standing("CHE", 3)));

        assertThat(json).contains("\"code\":\"ARS\"", "\"current\":2");
        assertThat(json).contains("\"code\":\"LIV\"", "\"current\":1");
    }

    @Test
    void liveRowsCarryNoScoreOfAnyKind() {
        // The distance is derived client-side precisely so nothing score-shaped is serialised.
        String json = rowsJson.liveRows(TABLE, Map.of(), List.of(standing("ARS", 2)));

        assertThat(json).doesNotContain("hit", "score", "zero", "bonus", "distance");
    }

    @Test
    void aTeamAbsentFromStandingsOmitsCurrentRatherThanFailing() {
        // Partial standings degrade per row: the club renders, it just has no reading yet.
        String json = rowsJson.liveRows(TABLE, Map.of(), List.of(standing("ARS", 1)));

        assertThat(json).contains("\"code\":\"CHE\"");
        assertThat(json).containsOnlyOnce("\"current\"");
    }

    @Test
    void liveRowsHandleEmptyAndNullStandings() {
        assertThat(rowsJson.liveRows(TABLE, Map.of(), List.of())).doesNotContain("current");
        assertThat(rowsJson.liveRows(TABLE, Map.of(), null)).doesNotContain("current");
        assertThat(rowsJson.liveRows(List.of(), Map.of(), List.of())).isEqualTo("[]");
    }
}
