package com.ligitabl.api.logging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logtail.logback.LogtailAppender;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Hardens LogtailAppender (0.3.1) against unserializable log events.
 *
 * <p>The vanilla appender serializes each event's raw arguments, and on a JsonProcessingException
 * its {@code flush()} never clears the batch, so one bad argument stops shipping until restart.
 * Here arguments are stringified (the formatted message already carries them), and a batch that
 * still fails is re-serialized event by event so only the offender is dropped.
 */
public class Jsr310LogtailAppender extends LogtailAppender {

    public Jsr310LogtailAppender() {
        super();
        dataMapper.registerModule(new JavaTimeModule());
    }

    @Override
    protected Map<String, Object> buildPostData(ILoggingEvent event) {
        var data = super.buildPostData(event);
        var args = event.getArgumentArray();
        if (args != null) {
            data.put("args", Arrays.stream(args).map(String::valueOf).toArray(String[]::new));
        }
        return data;
    }

    @Override
    protected String batchToJson(int size) throws JsonProcessingException {
        try {
            return super.batchToJson(size);
        } catch (JsonProcessingException e) {
            List<Map<String, Object>> serializable = new ArrayList<>();
            for (var event : new ArrayList<>(batch.subList(0, size))) {
                var data = buildPostData(event);
                try {
                    dataMapper.writeValueAsString(data);
                    serializable.add(data);
                } catch (JsonProcessingException dropped) {
                    errorLog.warn("Dropping unserializable log event from {}: {}", event.getLoggerName(), dropped.getMessage());
                }
            }
            return dataMapper.writeValueAsString(serializable);
        }
    }
}
