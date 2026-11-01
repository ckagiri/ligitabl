package com.ligitabl.api.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.model.outbox.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

/**
 * Deliberately delayed delivery: {@link OutboxEvent#createAvailableAt} parks a row in PENDING until
 * a chosen time. Real Postgres, because the claim compares {@code available_at <= now()} on the
 * database clock, which neither a mocked repo nor an injected {@link java.time.Clock} sees.
 */
@SpringBootTest
@DisplayName("Outbox delayed delivery (real Postgres)")
class OutboxDelayedDeliveryIT extends AbstractPostgresIT {

    private static final String TYPE = "IT_DELAYED";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxRepo outboxRepo;

    private OutboxEvent saveDue() {
        OutboxEvent event = OutboxEvent.create(key(), TYPE, "it", "due", "{}");
        assertThat(outboxRepo.save(event)).isTrue();
        return event;
    }

    private OutboxEvent saveDelayed(Duration delay) {
        OutboxEvent event = OutboxEvent.createAvailableAt(
                key(), TYPE, "it", "delayed", "{}", Instant.now().plus(delay));
        assertThat(outboxRepo.save(event)).isTrue();
        return event;
    }

    private static String key() {
        return "it-delayed:" + UUID.randomUUID();
    }

    private String statusOf(OutboxEvent event) {
        return jdbc.queryForObject("SELECT c_status FROM t_outbox_event WHERE pk_id = ?", String.class, event.getId());
    }

    @Test
    @DisplayName("a future available_at is written rather than defaulted to now()")
    void futureAvailableAtIsPersisted() {
        Instant before = Instant.now();
        OutboxEvent delayed = saveDelayed(Duration.ofDays(1));

        Instant stored = outboxRepo
                .findByIdempotencyKey(delayed.getIdempotencyKey())
                .orElseThrow()
                .getAvailableAt();

        // save() must write this column rather than leave it to the DB default.
        assertThat(stored).isAfter(before.plus(Duration.ofHours(23)));
    }

    @Test
    @DisplayName("a delayed event is not claimed while a due one alongside it is")
    void delayedEventIsNotClaimedEarly() {
        OutboxEvent due = saveDue();
        OutboxEvent delayed = saveDelayed(Duration.ofDays(1));

        List<UUID> claimed = outboxRepo.claimBatchForProcessing(50).stream()
                .map(OutboxEvent::getId)
                .toList();

        assertThat(claimed).contains(due.getId()).doesNotContain(delayed.getId());
        assertThat(statusOf(delayed)).isEqualTo(OutboxEvent.Status.PENDING.name());
    }

    @Test
    @DisplayName("the same event is claimed once its available_at has passed")
    void delayedEventIsClaimedOnceDue() {
        OutboxEvent delayed = saveDelayed(Duration.ofDays(1));
        assertThat(outboxRepo.claimBatchForProcessing(50))
                .noneMatch(e -> e.getId().equals(delayed.getId()));

        // Standing in for the day passing. Asserting only "not claimed early" would pass just as
        // well against an event that is never deliverable at all.
        jdbc.update(
                "UPDATE t_outbox_event SET c_available_at = now() - interval '1 minute' WHERE pk_id = ?",
                delayed.getId());

        assertThat(outboxRepo.claimBatchForProcessing(50))
                .anyMatch(e -> e.getId().equals(delayed.getId()));
    }

    @Test
    @DisplayName("resetStuckProcessing leaves a delayed PENDING row alone")
    void stuckSweepIgnoresDelayedPendingRows() {
        OutboxEvent delayed = saveDelayed(Duration.ofDays(1));

        // A far-future cutoff satisfies available_at < olderThan for every row, so only the status
        // filter keeps a delayed event from being reaped as "stuck".
        outboxRepo.resetStuckProcessing(Instant.now().plus(Duration.ofDays(365)));

        assertThat(statusOf(delayed)).isEqualTo(OutboxEvent.Status.PENDING.name());
        assertThat(outboxRepo
                        .findByIdempotencyKey(delayed.getIdempotencyKey())
                        .orElseThrow()
                        .getAvailableAt())
                .as("and its scheduled time is untouched")
                .isAfter(Instant.now().plus(Duration.ofHours(23)));
    }

    @Test
    @DisplayName("a null availableAt still falls back to the DB default, so existing callers are unaffected")
    void nullAvailableAtBehavesLikeCreate() {
        OutboxEvent due = saveDue();

        assertThat(outboxRepo
                        .findByIdempotencyKey(due.getIdempotencyKey())
                        .orElseThrow()
                        .getAvailableAt())
                .isNotNull()
                .isBefore(Instant.now().plusSeconds(1));
        assertThat(outboxRepo.claimBatchForProcessing(50))
                .anyMatch(e -> e.getId().equals(due.getId()));
    }
}
