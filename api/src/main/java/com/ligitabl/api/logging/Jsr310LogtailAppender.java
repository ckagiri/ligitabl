package com.ligitabl.api.logging;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logtail.logback.LogtailAppender;

/**
 * LogtailAppender builds its own internal ObjectMapper without the JSR-310 module. Any log
 * argument of type Instant/OffsetDateTime/etc. throws InvalidDefinitionException during
 * batchToJson(), which silently drops the entire batch it was bundled with (not just that one
 * event). Registering JavaTimeModule on the inherited mapper fixes this for every log call.
 */
public class Jsr310LogtailAppender extends LogtailAppender {

    public Jsr310LogtailAppender() {
        super();
        dataMapper.registerModule(new JavaTimeModule());
    }
}
