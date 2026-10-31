package com.ligitabl.api.usecase.notification.roundlocked;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.usecase.match.syncmatches.MatchSyncResult;
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.model.domain.league.RoundStatus;
import com.ligitabl.model.outbox.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

@ExtendWith(MockitoExtension.class)
class RecordRoundLockedUseCaseTest {

    @Mock
    OutboxRepo outboxRepo;

    @Test
    void lockedRound_writesIdempotentEvent() throws Exception {
        UUID roundId = UUID.randomUUID();
        var result = MatchSyncResult.builder()
                .seasonId(UUID.randomUUID())
                .roundId(roundId)
                .roundPosition(7)
                .roundStatus(RoundStatus.LOCKED)
                .build();

        new RecordRoundLockedUseCase(outboxRepo, new ObjectMapper()).execute(result);

        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(OutboxEventTypes.ROUND_LOCKED);
        assertThat(event.getValue().getIdempotencyKey()).isEqualTo("round-locked:" + roundId);
    }

    @Test
    void roundNotLocked_writesNothing() throws Exception {
        var result = MatchSyncResult.builder()
                .roundId(UUID.randomUUID())
                .roundStatus(RoundStatus.OPEN)
                .build();

        new RecordRoundLockedUseCase(outboxRepo, new ObjectMapper()).execute(result);

        verifyNoInteractions(outboxRepo);
    }
}
