package com.ligitabl.api.scheduling.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

/**
 * Fails when a {@code @Scheduled} method is neither heartbeat-monitored nor listed below, so
 * "added a background job and forgot to decide how we'd notice it breaking" is a build failure
 * rather than a five-day outage (.art/task_89.md).
 *
 * <p>Scans class metadata rather than the Spring context: tests run with
 * {@code ligitabl.scheduling.enabled=false}, so most job beans do not exist.
 */
class ScheduledJobRegistryTest {

    private static final String SCHEDULED = "org.springframework.scheduling.annotation.Scheduled";

    /** Jobs whose silence is deliberately not alerted on, with the reason. */
    private static final Map<String, String> KNOWN_UNMONITORED = Map.of(
            "EmailVerificationTokenCleanupJob#cleanupExpiredTokens",
                    "guarded with Sentry; failure means slow bloat on daily-expiring rows",
            "PasswordResetTokenCleanupJob#cleanupExpiredTokens",
                    "guarded with Sentry; failure means slow bloat on daily-expiring rows",
            "OutboxRelayJob#recoverStuckProcessing",
                    "same bean as OutboxRelayJob#relay, whose heartbeat already proves it is alive",
            "JoinReminderJob#run", "disabled in prod (JOIN_REMINDER_ENABLED=false); add a heartbeat when enabled",
            "JobWatchdog#checkHeartbeats", "the watchdog itself; covered by the [JOB_HEARTBEAT_SUMMARY] absence alert");

    /** Job classes that ping a heartbeat. Keep in step with JobNames. */
    private static final Set<String> MONITORED = Set.of(
            "MatchSyncScheduler",
            "OutboxRelayJob",
            "SeasonActivationService",
            "SeasonInPlayJob",
            "MatchdayAdvancementScheduler");

    @Test
    void everyScheduledMethodIsMonitoredOrExplicitlyExempt() throws IOException {
        var unaccounted = new TreeSet<String>();

        for (String method : findScheduledMethods()) {
            String simpleClass = method.substring(0, method.indexOf('#'));
            if (MONITORED.contains(simpleClass) || KNOWN_UNMONITORED.containsKey(method)) {
                continue;
            }
            unaccounted.add(method);
        }

        assertThat(unaccounted)
                .withFailMessage(
                        """
                        Unaccounted @Scheduled method(s): %s

                        Every background job needs a decision about how a silent failure is noticed:
                          - register a JobHeartbeat (see JobNames + docs/background-jobs.md), or
                          - add it to KNOWN_UNMONITORED in this test with the reason.
                        """,
                        unaccounted)
                .isEmpty();
    }

    /** Guards against an exemption outliving the method it describes. */
    @Test
    void knownUnmonitoredEntriesAllStillExist() throws IOException {
        List<String> actual = findScheduledMethods();

        assertThat(actual).containsAll(KNOWN_UNMONITORED.keySet());
    }

    private List<String> findScheduledMethods() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var factory = new CachingMetadataReaderFactory(resolver);
        var found = new ArrayList<String>();

        var resources = resolver.getResources("classpath*:com/ligitabl/api/**/*.class");
        assertThat(resources).as("classpath scan found no api classes").isNotEmpty();

        for (var resource : resources) {
            var metadata = factory.getMetadataReader(resource).getAnnotationMetadata();
            for (var method : metadata.getAnnotatedMethods(SCHEDULED)) {
                String simpleClass = metadata.getClassName()
                        .substring(metadata.getClassName().lastIndexOf('.') + 1);
                found.add(simpleClass + "#" + method.getMethodName());
            }
        }
        return found;
    }
}
