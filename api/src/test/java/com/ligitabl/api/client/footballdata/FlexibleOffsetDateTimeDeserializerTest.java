package com.ligitabl.api.client.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

class FlexibleOffsetDateTimeDeserializerTest {

    private final ObjectMapper objectMapper =
            JsonMapper.builder().findAndAddModules().build();

    @Test
    void competitionResponse_deserializesDateOnlyCurrentSeasonDates() throws Exception {
        String json = "{"
                + "\"id\":2021,"
                + "\"name\":\"Premier League\","
                + "\"code\":\"PL\","
                + "\"type\":\"LEAGUE\","
                + "\"emblem\":\"https://example.test/emblem.png\","
                + "\"currentSeason\":{"
                + "  \"id\":555,"
                + "  \"startDate\":\"2025-08-15\","
                + "  \"endDate\":\"2026-05-17\","
                + "  \"currentMatchday\":1"
                + "}"
                + "}";

        CompetitionResponse response = objectMapper.readValue(json, CompetitionResponse.class);

        assertThat(response.currentSeason().startDate().toString()).isEqualTo("2025-08-15T00:00Z");
        assertThat(response.currentSeason().endDate().toString()).isEqualTo("2026-05-17T00:00Z");
    }

    @Test
    void matchDto_deserializesDateOnlyUtcDate() throws Exception {
        String json = "{"
                + "\"id\":1,"
                + "\"utcDate\":\"2025-08-15\","
                + "\"status\":\"SCHEDULED\","
                + "\"matchday\":1,"
                + "\"stage\":\"REGULAR_SEASON\","
                + "\"homeTeam\":{\"id\":10,\"name\":\"A\",\"shortName\":\"A\",\"tla\":\"AAA\",\"crest\":\"\"},"
                + "\"awayTeam\":{\"id\":20,\"name\":\"B\",\"shortName\":\"B\",\"tla\":\"BBB\",\"crest\":\"\"},"
                + "\"score\":{\"duration\":\"REGULAR\",\"fullTime\":{\"home\":null,\"away\":null}}"
                + "}";

        MatchDto match = objectMapper.readValue(json, MatchDto.class);

        assertThat(match.utcDate().toString()).isEqualTo("2025-08-15T00:00Z");
    }
}
