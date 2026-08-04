package com.ligitabl.api.notification.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JoinReminderEnqueuerTest {

    private static final Instant NOW = Instant.parse("2026-07-26T09:00:00Z");
    private static final UUID SEASON_ID = UUID.randomUUID();
    /** Pre-season opened 40 days ago; predictions open in 10 days unless a test says otherwise. */
    private static final OffsetDateTime PRE_SEASON_OPENED =
            OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(40);
    private static final OffsetDateTime PREDICTIONS_OPEN =
            OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(10);

    @Mock
    OutboxRepo outboxRepo;

    @Mock
    UserRepo userRepo;

    @Mock
    SeasonRepo seasonRepo;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("pl");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JoinReminderProperties properties = new JoinReminderProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private JoinReminderEnqueuer enqueuer;

    @BeforeEach
    void setup() {
        enqueuer = new JoinReminderEnqueuer(
                outboxRepo, userRepo, seasonRepo, competitionDefaults, objectMapper, properties, clock);

        activeSeason(PRE_SEASON_OPENED, PREDICTIONS_OPEN);
        when(outboxRepo.existsSentEventsOfTypeSince(eq(OutboxEventTypes.ROUND_RESULTS), any()))
                .thenReturn(false);
        when(outboxRepo.save(any())).thenReturn(true);
    }

    private void activeSeason(OffsetDateTime preSeasonOpensAt, OffsetDateTime predictionsOpenAt) {
        Season season = Season.builder()
                .id(SEASON_ID)
                .preSeasonOpensAt(preSeasonOpensAt)
                .predictionsOpenAt(predictionsOpenAt)
                .build();
        when(seasonRepo.findActiveSeason("pl")).thenReturn(Optional.of(season));
    }

    private User userAgedDays(long days) {
        OffsetDateTime lastSeen = OffsetDateTime.ofInstant(NOW.minusSeconds(days * 86400), ZoneOffset.UTC);
        return User.builder()
                .id(UUID.randomUUID())
                .publicId(PublicId.create("abcdefghij"))
                .email(Email.create("user" + days + "@example.com"))
                .displayName("User")
                .emailVerified(true)
                .resultsEmailOptOut(false)
                .roles(Set.of())
                .createDate(lastSeen)
                .updateDate(lastSeen)
                .lastLoginAt(lastSeen)
                .build();
    }

    @Test
    void skipsEntirely_whenRoundResultsAlreadySentToday() {
        when(outboxRepo.existsSentEventsOfTypeSince(eq(OutboxEventTypes.ROUND_RESULTS), any()))
                .thenReturn(true);

        enqueuer.enqueueDueReminders();

        verifyNoInteractions(seasonRepo, userRepo);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skipsEntirely_whenJoinReminderAlreadyEnqueuedToday() {
        when(outboxRepo.existsEventsOfTypeCreatedSince(eq(OutboxEventTypes.JOIN_REMINDER), any()))
                .thenReturn(true);

        enqueuer.enqueueDueReminders();

        verifyNoInteractions(seasonRepo, userRepo);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skipsEntirely_whenSeasonWelcomeBatchEnqueuedToday() {
        // Season-opening day: the welcome batch reaches the same audience with the opposite
        // message ("your table is in"), so a reminder alongside it would contradict itself.
        when(outboxRepo.existsEventsOfTypeCreatedSince(eq(OutboxEventTypes.SEASON_WELCOME), any()))
                .thenReturn(true);

        enqueuer.enqueueDueReminders();

        verifyNoInteractions(seasonRepo, userRepo);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skipsEntirely_whenNoActiveSeason() {
        when(seasonRepo.findActiveSeason("pl")).thenReturn(Optional.empty());

        enqueuer.enqueueDueReminders();

        verifyNoInteractions(userRepo);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void enqueuesEarliestStage_forUserJustPastFirstThreshold() {
        User user = userAgedDays(1);
        when(userRepo.findUnjoinedUsersRegisteredBefore(eq(SEASON_ID), any(), any()))
                .thenReturn(List.of(user));

        enqueuer.enqueueDueReminders();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEvent event = captor.getValue();
        Assertions.assertThat(event.getIdempotencyKey()).isEqualTo("join-reminder:%s:1".formatted(user.getId()));
        Assertions.assertThat(event.getEventType()).isEqualTo(OutboxEventTypes.JOIN_REMINDER);
    }

    @Test
    void passesPreSeasonOpensAt_asStaleCutoff() {
        // The anchor, not a rolling window: someone last seen before the season opened never
        // saw it, so reminding them is a cold approach rather than a nudge.
        when(userRepo.findUnjoinedUsersRegisteredBefore(eq(SEASON_ID), any(), any()))
                .thenReturn(List.of());

        enqueuer.enqueueDueReminders();

        ArgumentCaptor<OffsetDateTime> staleCutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(userRepo).findUnjoinedUsersRegisteredBefore(eq(SEASON_ID), any(), staleCutoffCaptor.capture());
        Assertions.assertThat(staleCutoffCaptor.getValue()).isEqualTo(PRE_SEASON_OPENED);
    }

    @Test
    void skipsEntirely_whenSeasonHasNoPreSeasonOpensAt() {
        activeSeason(null, PREDICTIONS_OPEN);

        enqueuer.enqueueDueReminders();

        verifyNoInteractions(userRepo);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skipsEntirely_withinTheQuietPeriodBeforePredictionsOpen() {
        // Auto-join is hours away; "you haven't set your table" would be contradicted by the
        // welcome email the same day.
        activeSeason(PRE_SEASON_OPENED, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(3));

        enqueuer.enqueueDueReminders();

        verifyNoInteractions(userRepo);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void stillRuns_justOutsideTheQuietPeriod() {
        activeSeason(PRE_SEASON_OPENED, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(1).plusMinutes(1));
        when(userRepo.findUnjoinedUsersRegisteredBefore(eq(SEASON_ID), any(), any()))
                .thenReturn(List.of(userAgedDays(4)));

        enqueuer.enqueueDueReminders();

        verify(outboxRepo).save(any());
    }

    @Test
    void resumesAfterPredictionsHaveOpened_forMidSeasonSignups() {
        // The quiet period is one-sided. Once predictions are open the auto-join has already
        // emptied the eligible set, so anyone still matching is a fresh signup who does deserve
        // a nudge — silencing reminders for the rest of the season would drop them.
        activeSeason(PRE_SEASON_OPENED, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(3));
        when(userRepo.findUnjoinedUsersRegisteredBefore(eq(SEASON_ID), any(), any()))
                .thenReturn(List.of(userAgedDays(4)));

        enqueuer.enqueueDueReminders();

        verify(outboxRepo).save(any());
    }

    @Test
    void enqueuesOnlyTheLatestDueStage_forAnOldBacklogUser() {
        // 15 days old: eligible for stages 1, 4, and 11 simultaneously — must only get the
        // latest (11), never a backlog of all three in one run.
        User user = userAgedDays(15);
        when(userRepo.findUnjoinedUsersRegisteredBefore(eq(SEASON_ID), any(), any()))
                .thenReturn(List.of(user));

        enqueuer.enqueueDueReminders();

        verify(outboxRepo, times(1)).save(any());
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(captor.capture());
        Assertions.assertThat(captor.getValue().getIdempotencyKey())
                .isEqualTo("join-reminder:%s:11".formatted(user.getId()));
    }

    @Test
    void skipsUser_notYetDueForAnyStage() {
        User user = userAgedDays(0);
        when(userRepo.findUnjoinedUsersRegisteredBefore(eq(SEASON_ID), any(), any()))
                .thenReturn(List.of(user));

        enqueuer.enqueueDueReminders();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void onePoisonedUser_doesNotBlockOthers() {
        User bad = userAgedDays(4);
        User good = userAgedDays(4);
        when(userRepo.findUnjoinedUsersRegisteredBefore(eq(SEASON_ID), any(), any()))
                .thenReturn(List.of(bad, good));
        when(outboxRepo.save(any())).thenThrow(new RuntimeException("boom")).thenReturn(true);

        enqueuer.enqueueDueReminders();

        verify(outboxRepo, times(2)).save(any());
    }
}
