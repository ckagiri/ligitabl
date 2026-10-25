package com.ligitabl.api.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.logtail.logback.LogtailAppender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;

class Jsr310LogtailAppenderTest {

    /** Jackson fails on an object with no serializable properties. */
    private static final class NoProperties {
        @Override
        public String toString() {
            return "no-properties";
        }
    }

    /** Exposes the protected batch so a test can stage events without hitting the network. */
    private static final class TestableAppender extends Jsr310LogtailAppender {
        void stage(ILoggingEvent event) {
            batch.add(event);
        }

        String toJson() throws JsonProcessingException {
            return batchToJson(batch.size());
        }
    }

    private final LoggerContext context = new LoggerContext();
    private final TestableAppender appender = new TestableAppender();

    @AfterEach
    void tearDown() {
        appender.stop();
    }

    @Test
    void unserializableArgument_isShippedAsItsString() throws Exception {
        appender.stage(event("summary {}", new NoProperties()));

        assertThat(appender.toJson()).contains("\"args\":[\"no-properties\"]");
    }

    @Test
    void javaTimeArgument_stillSerializes() throws Exception {
        appender.stage(event("at {}", Instant.parse("2026-10-25T00:20:25Z")));

        assertThat(appender.toJson()).contains("2026-10-25T00:20:25Z");
    }

    @Test
    void healthyEventsInTheSameBatch_survive() throws Exception {
        appender.stage(event("first {}", 1));
        appender.stage(event("bad {}", new NoProperties()));
        appender.stage(event("last {}", "ok"));

        var json = appender.toJson();

        assertThat(json).contains("first 1").contains("bad no-properties").contains("last ok");
    }

    @Test
    void stockAppender_failsTheWholeBatchOnTheSameEvent() {
        var stock = new LogtailAppender() {
            String toJson(ILoggingEvent... events) throws JsonProcessingException {
                batch.addAll(java.util.List.of(events));
                return batchToJson(batch.size());
            }
        };
        try {
            assertThatThrownBy(() -> stock.toJson(event("first {}", 1), event("bad {}", new NoProperties())))
                    .isInstanceOf(JsonProcessingException.class);
        } finally {
            stock.stop();
        }
    }

    private ILoggingEvent event(String pattern, Object arg) {
        var logger = context.getLogger("com.ligitabl.api.scheduling.Test");
        return new LoggingEvent(getClass().getName(), logger, Level.INFO, pattern, null, new Object[] {arg});
    }
}
