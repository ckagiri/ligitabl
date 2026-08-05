package com.ligitabl.api.notification.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeasonWelcomeEmailEnqueuerTest {

    private static final UUID SEASON_ID = UUID.randomUUID();

    @Mock
    UserRepo userRepo;

    @Mock
    OutboxRepo outboxRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SeasonWelcomeEmailEnqueuer enqueuer;

    @BeforeEach
    void setup() {
        enqueuer = new SeasonWelcomeEmailEnqueuer(userRepo, outboxRepo, objectMapper);
        when(outboxRepo.save(any())).thenReturn(true);
    }

    private User user(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .publicId(PublicId.create("abcdefghij"))
                .email(Email.create(email))
                .displayName("User")
                .emailVerified(true)
                .resultsEmailOptOut(false)
                .roles(Set.of())
                .build();
    }

    @Test
    void writesOneEventPerRecipientKeyedByUserAndSeason() {
        User alice = user("alice@x.com");
        User bob = user("bob@x.com");
        when(userRepo.findMailableUsersWithPreSeasonRegistration(SEASON_ID)).thenReturn(List.of(alice, bob));

        enqueuer.enqueue(new SeasonWelcomeFanoutPayload(SEASON_ID));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo, times(2)).save(captor.capture());
        Assertions.assertThat(captor.getAllValues())
                .extracting(OutboxEvent::getIdempotencyKey)
                .containsExactlyInAnyOrder(
                        "season-welcome:%s:%s".formatted(alice.getId(), SEASON_ID),
                        "season-welcome:%s:%s".formatted(bob.getId(), SEASON_ID));
        Assertions.assertThat(captor.getAllValues())
                .allSatisfy(e -> Assertions.assertThat(e.getEventType()).isEqualTo(OutboxEventTypes.SEASON_WELCOME));
    }

    @Test
    void keyIsSeasonScoped_soANewSeasonCanWelcomeTheSameUserAgain() {
        User alice = user("alice@x.com");
        UUID nextSeason = UUID.randomUUID();
        when(userRepo.findMailableUsersWithPreSeasonRegistration(nextSeason)).thenReturn(List.of(alice));

        enqueuer.enqueue(new SeasonWelcomeFanoutPayload(nextSeason));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(captor.capture());
        Assertions.assertThat(captor.getValue().getIdempotencyKey())
                .isEqualTo("season-welcome:%s:%s".formatted(alice.getId(), nextSeason));
    }

    @Test
    void carriesTheAddressInThePayload() {
        when(userRepo.findMailableUsersWithPreSeasonRegistration(SEASON_ID)).thenReturn(List.of(user("carol@x.com")));

        enqueuer.enqueue(new SeasonWelcomeFanoutPayload(SEASON_ID));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(captor.capture());
        Assertions.assertThat(captor.getValue().getPayload()).contains("carol@x.com");
    }

    @Test
    void onePoisonedRecipient_doesNotCostTheOthersTheirEmail() {
        when(userRepo.findMailableUsersWithPreSeasonRegistration(SEASON_ID))
                .thenReturn(List.of(user("bad@x.com"), user("good@x.com")));
        when(outboxRepo.save(any())).thenThrow(new RuntimeException("boom")).thenReturn(true);

        enqueuer.enqueue(new SeasonWelcomeFanoutPayload(SEASON_ID));

        verify(outboxRepo, times(2)).save(any());
    }

    @Test
    void noRecipients_writesNothing() {
        when(userRepo.findMailableUsersWithPreSeasonRegistration(SEASON_ID)).thenReturn(List.of());

        enqueuer.enqueue(new SeasonWelcomeFanoutPayload(SEASON_ID));

        verify(outboxRepo, never()).save(any());
    }
}
