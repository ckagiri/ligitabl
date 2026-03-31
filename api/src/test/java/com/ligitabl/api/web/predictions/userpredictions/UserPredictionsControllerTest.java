package com.ligitabl.api.web.predictions.userpredictions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;

class UserPredictionsControllerTest {

    private final UserPredictionsController controller =
            new UserPredictionsController(new ObjectMapper(), null, null, null, null, null, null, null, null, null);

    @Test
    @DisplayName("toFixture maps finished home fixture to win result")
    void toFixture_mapsFinishedHomeFixtureToWinResult() {
        var arsenal = team("ARS", "Arsenal");
        var chelsea = team("CHE", "Chelsea");
        var match = match(arsenal, chelsea, MatchStatus.FINISHED, 2, 1);

        var fixture = controller.toFixture("ARS", match);

        assertThat(fixture.opponent()).isEqualTo("CHE");
        assertThat(fixture.isHome()).isTrue();
        assertThat(fixture.status()).isEqualTo("FINISHED");
        assertThat(fixture.result()).isEqualTo("WIN");
    }

    @Test
    @DisplayName("toFixture maps finished away fixture result from away perspective")
    void toFixture_mapsFinishedAwayFixtureResultFromAwayPerspective() {
        var arsenal = team("ARS", "Arsenal");
        var chelsea = team("CHE", "Chelsea");
        var match = match(arsenal, chelsea, MatchStatus.FINISHED, 2, 1);

        var fixture = controller.toFixture("CHE", match);

        assertThat(fixture.opponent()).isEqualTo("ARS");
        assertThat(fixture.isHome()).isFalse();
        assertThat(fixture.status()).isEqualTo("FINISHED");
        assertThat(fixture.result()).isEqualTo("LOSS");
    }

    @Test
    @DisplayName("toFixture keeps live fixtures orange-ready and without final result")
    void toFixture_keepsLiveFixturesWithoutFinalResult() {
        var arsenal = team("ARS", "Arsenal");
        var chelsea = team("CHE", "Chelsea");
        var match = match(arsenal, chelsea, MatchStatus.SUSPENDED, 1, 0);

        var fixture = controller.toFixture("ARS", match);

        assertThat(fixture.status()).isEqualTo("LIVE");
        assertThat(fixture.result()).isNull();
    }

    @Test
    @DisplayName("toFixture preserves POSTPONED status for postponed fixtures")
    void toFixture_preservesPostponedStatus() {
        var arsenal = team("ARS", "Arsenal");
        var chelsea = team("CHE", "Chelsea");
        var match = match(arsenal, chelsea, MatchStatus.POSTPONED, null, null);

        var fixture = controller.toFixture("ARS", match);

        assertThat(fixture.status()).isEqualTo("POSTPONED");
        assertThat(fixture.result()).isNull();
    }

    private static Match match(Team home, Team away, MatchStatus status, Integer homeGoals, Integer awayGoals) {
        var match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1001)
                .homeTeamId(home.getId())
                .awayTeamId(away.getId())
                .seasonId(UUID.randomUUID())
                .roundId(UUID.randomUUID())
                .slug(home.getCode().toLowerCase() + "-vs-" + away.getCode().toLowerCase())
                .status(status)
                .matchday(1)
                .build();
        match.setTeams(home, away);
        if (homeGoals != null && awayGoals != null) {
            match.setScore(homeGoals, awayGoals);
        }
        return match;
    }

    private static Team team(String code, String name) {
        return Team.builder()
                .id(UUID.randomUUID())
                .name(name)
                .shortName(name)
                .slug(TeamSlug.of(code.toLowerCase()))
                .tla(code)
                .build();
    }
}
