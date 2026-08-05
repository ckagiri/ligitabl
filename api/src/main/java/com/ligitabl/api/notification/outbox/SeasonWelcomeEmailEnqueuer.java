package com.ligitabl.api.notification.outbox;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Expands the season-welcome fan-out into one SEASON_WELCOME event per recipient.
 *
 * <p>Recipients are round-0 holders — auto-joined and genuinely pre-registered alike — because
 * that is exactly the set for whom "you still have up to 5 swaps" is true.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeasonWelcomeEmailEnqueuer {

    private final UserRepo userRepo;
    private final OutboxRepo outboxRepo;
    private final ObjectMapper objectMapper;

    public void enqueue(SeasonWelcomeFanoutPayload event) {
        List<User> recipients = userRepo.findMailableUsersWithPreSeasonRegistration(event.seasonId());
        if (recipients.isEmpty()) {
            log.info("[SEASON_WELCOME_ENQUEUED] seasonId={}, recipients=0", event.seasonId());
            return;
        }

        int inserted = 0;
        for (User user : recipients) {
            try {
                String json = objectMapper.writeValueAsString(new SeasonWelcomePayload(
                        user.getId(), user.getEmail().value()));
                OutboxEvent welcome = OutboxEvent.create(
                        "season-welcome:%s:%s".formatted(user.getId(), event.seasonId()),
                        OutboxEventTypes.SEASON_WELCOME,
                        "user",
                        user.getId().toString(),
                        json);
                if (outboxRepo.save(welcome)) {
                    inserted++;
                }
            } catch (Exception e) {
                // One bad payload must not cost the other recipients their email.
                log.error("[SEASON_WELCOME_ENQUEUE_USER_FAILED] userId={}: {}", user.getId(), e.getMessage(), e);
            }
        }

        log.info(
                "[SEASON_WELCOME_ENQUEUED] seasonId={}, recipients={}, inserted={}",
                event.seasonId(),
                recipients.size(),
                inserted);
    }
}
