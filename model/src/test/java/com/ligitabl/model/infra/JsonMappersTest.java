package com.ligitabl.model.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.domain.RoundSwap;
import com.ligitabl.model.domain.SwapChange;

/**
 * Guards the jsonb Instant encoding. Without {@link JsonMappers} these cases fail on the model test
 * classpath — jackson-datatype-jsr310 is not a dependency of this module, so
 * {@code findAndRegisterModules()} finds no Instant handling and every SwapChange throws.
 */
class JsonMappersTest {

    private static final Instant WHOLE = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant WITH_NANOS = Instant.ofEpochSecond(1785578400L, 123456789);

    private final ObjectMapper mapper = JsonMappers.forJsonb();

    @Test
    void serializesInstantWithoutJsr310OnTheClasspath() throws Exception {
        String json = mapper.writeValueAsString(new SwapChange(WHOLE, "ARS:1→2", "CHE:2→1"));

        assertThat(json).contains("\"teamA\":\"ARS:1→2\"");
        assertThat(json).contains("1785578400");
    }

    @Test
    void encodesInstantAsDecimalEpochSecondsMatchingJsr310() throws Exception {
        // Pins the wire format: existing t_season_prediction.c_swaps rows were written through the
        // api module with jsr310 active, which emits decimal epoch seconds. Changing this would
        // orphan already-stored data.
        String json = mapper.writeValueAsString(WITH_NANOS);

        assertThat(json).isEqualTo("1785578400.123456789");
    }

    @Test
    void wholeSecondsKeepTheNineDecimalPlacesJsr310Writes() throws Exception {
        assertThat(mapper.writeValueAsString(WHOLE)).isEqualTo("1785578400.000000000");
    }

    @Test
    void roundTripsNestedRoundSwaps() throws Exception {
        // The exact shape stored in t_season_prediction.c_swaps: RoundSwap nesting SwapChange.
        List<RoundSwap> swaps = List.of(new RoundSwap(3, List.of(new SwapChange(WITH_NANOS, "ARS:1→4", "CHE:4→1"))));

        String json = mapper.writeValueAsString(swaps);
        List<RoundSwap> back =
                mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, RoundSwap.class));

        assertThat(back).hasSize(1);
        assertThat(back.get(0).getRound()).isEqualTo(3);
        assertThat(back.get(0).getChanges().get(0).timestamp()).isEqualTo(WITH_NANOS);
        assertThat(back.get(0).getChanges().get(0).teamA()).isEqualTo("ARS:1→4");
    }

    @Test
    void readsBackDecimalEpochSeconds() throws Exception {
        assertThat(mapper.readValue("1785578400.123456789", Instant.class)).isEqualTo(WITH_NANOS);
    }

    @Test
    void alsoReadsIntegerSecondsAndIsoText() throws Exception {
        // Tolerant on read so any hand-written or older-format value still loads.
        assertThat(mapper.readValue("1785578400", Instant.class)).isEqualTo(WHOLE);
        assertThat(mapper.readValue("\"2026-08-01T10:00:00Z\"", Instant.class)).isEqualTo(WHOLE);
    }
}
