package com.ligitabl.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.notification.email.EmailService;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminNotificationService")
class AdminNotificationServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private SlackNotificationService slackNotificationService;

    @InjectMocks
    private AdminNotificationService service;

    private final UUID roundId = UUID.randomUUID();
    private final UUID seasonId = UUID.randomUUID();

    // --- alert tier: Slack + email ---

    @Test
    void circuitBreakerOpened_alertsSlackAndEmail() {
        service.notifyCircuitBreakerOpened(10, 30);

        verify(slackNotificationService).send(anyString());
        verify(emailService).sendAdminAlert(anyString(), anyString());
    }

    @Test
    void blockedFinalization_alertsSlackAndEmail() {
        service.notifyBlockedFinalization(roundId, 5, List.of(UUID.randomUUID()), List.of("- Match ID: x"));

        verify(slackNotificationService).send(anyString());
        verify(emailService).sendAdminAlert(anyString(), anyString());
    }

    @Test
    void advancementFailed_alertsSlackAndEmail() {
        service.notifyAdvancementFailed(roundId, "boom");

        verify(slackNotificationService).send(anyString());
        verify(emailService).sendAdminAlert(anyString(), anyString());
    }

    // --- info tier: Slack only ---

    @Test
    void circuitBreakerRecovered_slackOnly() {
        service.notifyCircuitBreakerRecovered(10);

        verify(slackNotificationService).send(anyString());
        verify(emailService, never()).sendAdminAlert(anyString(), anyString());
    }

    @Test
    void startup_slackOnly() {
        service.notifyStartup("PL");

        verify(slackNotificationService).send(anyString());
        verify(emailService, never()).sendAdminAlert(anyString(), anyString());
    }

    @Test
    void roundFinalized_slackOnly() {
        service.notifyRoundFinalized(roundId, 5, 100, 100, false);

        verify(slackNotificationService).send(anyString());
        verify(emailService, never()).sendAdminAlert(anyString(), anyString());
    }

    @Test
    void advancementScheduled_slackOnly() {
        service.notifyAdvancementScheduled(roundId, 5, OffsetDateTime.now().plusMinutes(3), 3);

        verify(slackNotificationService).send(anyString());
        verify(emailService, never()).sendAdminAlert(anyString(), anyString());
    }

    @Test
    void roundAdvanced_slackOnly() {
        service.notifyRoundAdvanced(5, seasonId, false);

        verify(slackNotificationService).send(anyString());
        verify(emailService, never()).sendAdminAlert(anyString(), anyString());
    }

    @Test
    void matchdayAdvanced_slackOnly() {
        service.notifyMatchdayAdvanced(22, 23, seasonId);

        verify(slackNotificationService).send(anyString());
        verify(emailService, never()).sendAdminAlert(anyString(), anyString());
    }

    @Test
    void seasonActivated_slackOnly() {
        service.notifySeasonActivated("premier-league", UUID.randomUUID(), seasonId);

        verify(slackNotificationService).send(anyString());
        verify(emailService, never()).sendAdminAlert(anyString(), anyString());
    }

    // --- new-user signup: NEW_USERS channel, Slack only ---

    @Test
    void newUserSignup_sendsToNewUsersChannel_noEmail() {
        service.notifyNewUserSignup("AbCd3fGh9J", "Jane Doe", "jane@example.com", "email registration");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(slackNotificationService).send(eq(SlackNotificationService.Channel.NEW_USERS), captor.capture());
        verify(emailService, never()).sendAdminAlert(anyString(), anyString());
        assertThat(captor.getValue())
                .startsWith("🎉")
                .contains("Jane Doe")
                .contains("jane@example.com")
                .contains("email registration")
                .contains("AbCd3fGh9J");
    }

    // --- message content spot checks ---

    @Test
    void syncScheduleChanged_includesReasonRoundAndDelay() {
        service.notifySyncScheduleChanged(roundId, 22, Duration.ofSeconds(90), "Live matches in progress");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(slackNotificationService).send(captor.capture());
        assertThat(captor.getValue())
                .contains("Live matches in progress")
                .contains("22")
                .contains(roundId.toString())
                .contains("1 minute");
    }

    @Test
    void alertMessages_carryAlertPrefix_infoMessagesCarryInfoPrefix() {
        service.notifyAdvancementFailed(roundId, "boom");
        service.notifyMatchdayAdvanced(22, 23, seasonId);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(slackNotificationService, times(2)).send(captor.capture());
        assertThat(captor.getAllValues().get(0)).startsWith("🔴");
        assertThat(captor.getAllValues().get(1)).startsWith("ℹ️");
    }
}
