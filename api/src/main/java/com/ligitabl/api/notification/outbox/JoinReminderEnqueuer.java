package com.ligitabl.api.notification.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Each user gets at most one email per run, at the <em>latest</em> stage their time since last
 * seen.
 *
 * <p>Gated by two daily throttles, both evaluated against UTC-midnight "today":
 * <ul>
 *   <li>if a ROUND_RESULTS batch already went out today, this run is skipped entirely, to keep
 *       combined daily send volume under the free-tier cap;
 *   <li>if a JOIN_REMINDER batch has already been enqueued today, this run is skipped — this is
 *       what makes it safe to also invoke this method from a startup recovery hook (see
 *       {@code JoinReminderStartupRecovery}) without risking a duplicate same-day run alongside
 *       the 9am cron.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JoinReminderEnqueuer {

    private final OutboxRepo outboxRepo;
    private final UserRepo userRepo;
    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;
    private final ObjectMapper objectMapper;
    private final JoinReminderProperties properties;
    private final Clock clock;

    public void enqueueDueReminders() {
        OffsetDateTime todayStart = OffsetDateTime.now(clock)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toOffsetDateTime();
        if (outboxRepo.existsSentEventsOfTypeSince(OutboxEventTypes.ROUND_RESULTS, todayStart.toInstant())) {
            log.info("[JOIN_REMINDER_SKIPPED] a ROUND_RESULTS batch already sent today; skipping this run");
            return;
        }
        if (outboxRepo.existsEventsOfTypeCreatedSince(OutboxEventTypes.JOIN_REMINDER, todayStart.toInstant())) {
            log.info("[JOIN_REMINDER_SKIPPED] a JOIN_REMINDER batch was already enqueued today; skipping this run");
            return;
        }
        if (outboxRepo.existsEventsOfTypeCreatedSince(OutboxEventTypes.SEASON_WELCOME, todayStart.toInstant())) {
            log.info("[JOIN_REMINDER_SKIPPED] a SEASON_WELCOME batch was enqueued today; skipping this run");
            return;
        }

        Season season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        if (season == null) {
            log.info("[JOIN_REMINDER_SKIPPED] no active season");
            return;
        }

        List<Integer> stages = properties.getStageDays().stream().sorted().toList();
        if (stages.isEmpty()) {
            return;
        }

        if (season.getPreSeasonOpensAt() == null) {
            log.info("[JOIN_REMINDER_SKIPPED] season {} has no preSeasonOpensAt to anchor to", season.getId());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        if (inQuietPeriodBeforePredictionsOpen(season, now)) {
            log.info(
                    "[JOIN_REMINDER_SKIPPED] within {} day(s) of predictions opening for season {}; "
                            + "auto-join is about to give these users a table",
                    properties.getQuietDaysBeforePredictionsOpen(),
                    season.getId());
            return;
        }

        OffsetDateTime earliestCutoff = now.minusDays(stages.get(0));
        OffsetDateTime staleCutoff = season.getPreSeasonOpensAt();
        List<User> candidates = userRepo.findUnjoinedUsersRegisteredBefore(season.getId(), earliestCutoff, staleCutoff);

        int inserted = 0;
        for (User user : candidates) {
            try {
                OffsetDateTime lastSeen = user.getLastLoginAt() != null ? user.getLastLoginAt() : user.getUpdateDate();
                long daysSinceLastSeen = Duration.between(lastSeen, now).toDays();
                int stage = latestDueStage(stages, daysSinceLastSeen);
                if (stage == -1) {
                    continue; // defensive: shouldn't happen given earliestCutoff
                }

                JoinReminderPayload payload =
                        new JoinReminderPayload(user.getId(), user.getEmail().value(), stage);
                String json = objectMapper.writeValueAsString(payload);
                String idempotencyKey = "join-reminder:%s:%d".formatted(user.getId(), stage);
                OutboxEvent event = OutboxEvent.create(
                        idempotencyKey,
                        OutboxEventTypes.JOIN_REMINDER,
                        "user",
                        user.getId().toString(),
                        json);
                if (outboxRepo.save(event)) {
                    inserted++;
                }
            } catch (Exception e) {
                // One bad payload must not cost the other candidates their email.
                log.error("[JOIN_REMINDER_ENQUEUE_USER_FAILED] userId={}: {}", user.getId(), e.getMessage(), e);
            }
        }

        log.info("[JOIN_REMINDER_ENQUEUED] candidates={}, inserted={}", candidates.size(), inserted);
    }

    /**
     * True in the window immediately before predictions open. Only ever suppresses runs
     * <em>before</em> the transition: once predictions are open the window is behind us, and
     * reminders resume for whoever is still legitimately unjoined (mid-season signups).
     */
    private boolean inQuietPeriodBeforePredictionsOpen(Season season, OffsetDateTime now) {
        OffsetDateTime predictionsOpenAt = season.getPredictionsOpenAt();
        if (predictionsOpenAt == null) {
            return false;
        }
        OffsetDateTime quietFrom = predictionsOpenAt.minusDays(properties.getQuietDaysBeforePredictionsOpen());
        return !now.isBefore(quietFrom) && now.isBefore(predictionsOpenAt);
    }

    /** The largest configured stage the user's time-since-last-seen has reached, or -1 if none. */
    private int latestDueStage(List<Integer> sortedStages, long daysSinceLastSeen) {
        int due = -1;
        for (int stage : sortedStages) {
            if (daysSinceLastSeen >= stage) {
                due = stage;
            } else {
                break;
            }
        }
        return due;
    }
}
