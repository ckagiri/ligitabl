package com.ligitabl.model.infra;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Shared {@link ObjectMapper} factory for the jsonb columns this module reads and writes.
 *
 * <p>Exists because {@code findAndRegisterModules()} is a runtime classpath lookup and this module
 * does not depend on jackson-datatype-jsr310. Inside the api module Spring Boot supplies that jar
 * transitively, so {@code Instant} serializes; on the model module's own test classpath it does
 * not, and every {@code SwapChange} — which carries an {@code Instant} timestamp — fails with
 * "Java 8 date/time type not supported by default". Registering the handling explicitly makes the
 * behaviour the same in both places instead of depending on who happens to be on the classpath.
 *
 * <p><b>The encoding deliberately matches what jackson-datatype-jsr310 produces by default</b>: a
 * decimal epoch-seconds number such as {@code 1785578400.000000000}. Existing
 * {@code t_season_prediction.c_swaps} data was written that way through the api module, so
 * switching to ISO-8601 text here would leave already-stored rows in a format this mapper no longer
 * writes. Reads accept both encodings regardless, so either form round-trips.
 */
public final class JsonMappers {

    private JsonMappers() {}

    /** A mapper that handles {@code Instant} identically with or without jsr310 on the classpath. */
    public static ObjectMapper forJsonb() {
        return new ObjectMapper().findAndRegisterModules().registerModule(instantModule());
    }

    private static SimpleModule instantModule() {
        SimpleModule module = new SimpleModule();
        // Registered after findAndRegisterModules() so it wins over jsr310's handlers when that jar
        // is present, keeping api and model byte-identical rather than merely similar.
        module.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeNumber(BigDecimal.valueOf(value.getEpochSecond())
                        .add(BigDecimal.valueOf(value.getNano(), 9))
                        .setScale(9));
            }
        });
        module.addDeserializer(Instant.class, new JsonDeserializer<>() {
            @Override
            public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonToken token = p.currentToken();
                if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                    BigDecimal seconds = p.getDecimalValue();
                    long epochSecond = seconds.longValue();
                    int nanos = seconds.subtract(BigDecimal.valueOf(epochSecond))
                            .movePointRight(9)
                            .intValue();
                    return Instant.ofEpochSecond(epochSecond, nanos);
                }
                if (token == JsonToken.VALUE_NUMBER_INT) {
                    // jsr310 writes whole seconds without a fractional part when nanos are zero.
                    return Instant.ofEpochSecond(p.getLongValue());
                }
                return Instant.parse(p.getText());
            }
        });
        return module;
    }
}
