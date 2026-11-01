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
 * <p>Registers Instant handling explicitly: {@code findAndRegisterModules()} only finds
 * jackson-datatype-jsr310 when the api module puts it on the classpath, and this module does not
 * depend on it.
 *
 * <p>Writes decimal epoch seconds such as {@code 1785578400.000000000}, jsr310's default, because
 * stored {@code t_season_prediction.c_swaps} rows use it. Reads accept ISO-8601 too.
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
