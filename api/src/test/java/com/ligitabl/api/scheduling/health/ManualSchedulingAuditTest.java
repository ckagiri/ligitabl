package com.ligitabl.api.scheduling.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Pins the set of classes that arm their own next run via {@code taskScheduler.schedule(...)}.
 *
 * <p>⚠️ This cannot verify that such a class re-arms on every code path — that needs control-flow
 * analysis. All it does is force a human to read the 2026-09-06 incident note before adding a
 * third site, because the failure mode is not obvious and cost five days of silent downtime.
 * {@code MatchSyncSchedulerTest#reentrantSkip_stillSchedulesNextSync} is the real behavioural guard.
 */
class ManualSchedulingAuditTest {

    private static final String PATTERN = "taskScheduler.schedule(";

    /**
     * MatchSyncScheduler re-arms a chain (the thing that broke). RoundAdvancementService fires a
     * one-shot but persists advance_at and has RoundAdvancementRecovery sweep it on startup — the
     * pattern to copy if a third site is ever needed.
     */
    private static final Set<String> ALLOWED = Set.of("MatchSyncScheduler.java", "RoundAdvancementService.java");

    @Test
    void onlyKnownClassesScheduleTheirOwnTasks() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        assumeTrue(Files.isDirectory(sourceRoot), "run from the api module directory");

        var offenders = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains(PATTERN)) {
                    String name = file.getFileName().toString();
                    if (!ALLOWED.contains(name)) {
                        offenders.add(name);
                    }
                }
            }
        }

        assertThat(offenders)
                .withFailMessage(
                        """
                        New manual taskScheduler.schedule(...) site(s): %s

                        A self-rescheduling chain breaks permanently if any path returns without
                        re-arming — that is the 2026-09-06 match-sync stall (.art/task_89.md).
                        Prefer @Scheduled(fixedDelay), which Spring re-queues for you. If a one-shot
                        is genuinely needed, persist its due time and sweep on startup the way
                        RoundAdvancementService does, then add it to ALLOWED here.
                        """,
                        offenders)
                .isEmpty();
    }
}
