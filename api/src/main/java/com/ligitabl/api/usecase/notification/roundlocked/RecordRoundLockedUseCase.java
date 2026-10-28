package com.ligitabl.api.usecase.notification.roundlocked;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.usecase.match.syncmatches.MatchSyncResult;
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.api.usecase.shared.outbox.RoundLockedPayload;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.repo.OutboxRepo;

import lombok.RequiredArgsConstructor;

/**
 * Writes a ROUND_LOCKED outbox event whenever a sync finds the current round LOCKED. The
 * idempotency key ("round-locked:{roundId}") means only the first write per round inserts
 * (ON CONFLICT DO NOTHING); every later sync tick while the round stays LOCKED is a no-op.
 */
@Service
@RequiredArgsConstructor
public class RecordRoundLockedUseCase {

    private final OutboxRepo outboxRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public void execute(MatchSyncResult result) throws JsonProcessingException {
        if (result.roundId() == null || result.roundStatus() != RoundStatus.LOCKED) {
            return;
        }
        var payload = new RoundLockedPayload(result.seasonId(), result.roundId(), result.roundPosition());
        outboxRepo.save(OutboxEvent.create(
                "round-locked:" + result.roundId(),
                OutboxEventTypes.ROUND_LOCKED,
                "round",
                result.roundId().toString(),
                objectMapper.writeValueAsString(payload)));
    }
}
