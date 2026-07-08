package com.ligitabl.api.web.shared.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.TeamSlug;

class SharePredictionTextBuilderTest {

    private final SharePredictionTextBuilder builder = new SharePredictionTextBuilder();

    @Test
    void shortList_includesEveryTeam_noEllipsis() {
        List<TeamRank> rankings = List.of(TeamRank.of("ARS", 1), TeamRank.of("CHE", 2), TeamRank.of("LIV", 3));
        Map<String, Team> teamsByCode = teamsByCode(rankings, code -> code);

        String text = builder.build(13, rankings, teamsByCode, "https://ligipredictor.com/u/T2ADsSc8hQ/2526/gw/13");

        assertThat(text).contains("13");
        assertThat(text).contains("1 ARS", "2 CHE", "3 LIV");
        assertThat(text).doesNotContain("...");
        assertThat(text).contains("LigiPredictor");
        assertThat(text).contains("ligipredictor.com/u/T2ADsSc8hQ/2526/gw/13");
        assertThat(text).doesNotContain("https://");
    }

    @Test
    void longList_showsFirstFiveEllipsisLastThree() {
        List<TeamRank> rankings = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> TeamRank.of("T" + i, i))
                .toList();
        Map<String, Team> teamsByCode = teamsByCode(rankings, code -> code);

        String text = builder.build(13, rankings, teamsByCode, "https://ligipredictor.com/u/T2ADsSc8hQ/2526/gw/13");

        assertThat(text).contains("1 T1", "2 T2", "3 T3", "4 T4", "5 T5");
        assertThat(text).contains("18 T18", "19 T19", "20 T20");
        assertThat(text).doesNotContain("6 T6", "17 T17");
        assertThat(text).contains("...");
    }

    @Test
    void usesShorterNameWhenPresent_fallsBackToShortName() {
        Team spurs = Team.builder()
                .id(UUID.randomUUID())
                .name("Tottenham Hotspur")
                .shortName("Tottenham")
                .shorterName("Spurs")
                .slug(TeamSlug.of("tottenham"))
                .tla("TOT")
                .build();
        Team noShorter = Team.builder()
                .id(UUID.randomUUID())
                .name("Arsenal")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();
        List<TeamRank> rankings = List.of(TeamRank.of("TOT", 1), TeamRank.of("ARS", 2));
        Map<String, Team> teamsByCode = Map.of("TOT", spurs, "ARS", noShorter);

        String text = builder.build(13, rankings, teamsByCode, "https://ligipredictor.com/u/abc/2526/gw/13");

        assertThat(text).contains("1 Spurs");
        assertThat(text).contains("2 Arsenal");
        assertThat(text).doesNotContain("Tottenham");
    }

    private static Map<String, Team> teamsByCode(List<TeamRank> rankings, Function<String, String> nameFor) {
        return rankings.stream().map(TeamRank::getCode).collect(Collectors.toMap(code -> code, code -> Team.builder()
                .id(UUID.randomUUID())
                .name(nameFor.apply(code))
                .shortName(nameFor.apply(code))
                .slug(TeamSlug.of(code.toLowerCase()))
                .tla(code.length() >= 3 ? code.substring(0, 3) : (code + "XXX").substring(0, 3))
                .build()));
    }
}
