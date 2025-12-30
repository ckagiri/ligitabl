package com.ligitabl.api.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationService {
    private final EmailService emailService;

    public void notifyCircuitBreakerOpened(int consecutiveFailures, long recoveryWaitHours) {
        String subject = "Match sync circuit breaker OPEN";
        String body = "Circuit breaker opened after " + consecutiveFailures + " consecutive failures.\n"
                + "Will retry after " + recoveryWaitHours + " hour(s).";

        log.warn("{}: {}", subject, body);
        emailService.sendAdminAlert(subject, body);
    }

    public void notifyCircuitBreakerRecovered(int previousFailures) {
        String subject = "Match sync circuit breaker RECOVERED";
        String body = "Circuit breaker recovered after " + previousFailures + " failures.";

        log.info("{}: {}", subject, body);
        emailService.sendAdminAlert(subject, body);
    }

    public void notifyBlockedFinalization(
            UUID roundId, int roundPosition, List<UUID> blockingMatchIds, List<String> matchDetails) {
        String subject = "Round finalization BLOCKED";
        String body = "Round finalization blocked.\n" + "Round ID: "
                + roundId + "\n" + "Round position: "
                + roundPosition + "\n" + "Blocking matches: "
                + blockingMatchIds.size() + "\n\n" + String.join("\n", matchDetails);

        log.warn("{}: {}", subject, body);
        emailService.sendAdminAlert(subject, body);
    }
}
