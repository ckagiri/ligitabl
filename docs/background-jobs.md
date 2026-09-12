# Background Jobs

How scheduled work is written, guarded, and monitored in this repo. Read this before adding a
`@Scheduled` method — several of the rules here are enforced only by a comment or a convention, and
the one that was not written down is what caused the outage described at the bottom.

## The job table

| Job | Trigger | Heartbeat | Declared max interval |
|---|---|---|---|
| `MatchSyncScheduler` | self-armed chain | `matchSync` | 30h |
| `OutboxRelayJob#relay` | `fixedDelay` 15s | `outboxRelay` | 5m |
| `OutboxRelayJob#recoverStuckProcessing` | `fixedDelay` 5m | — | covered by `relay` |
| `SeasonActivationService` | `fixedDelay` 15m | `seasonActivation` | 45m |
| `SeasonInPlayJob` | `fixedDelay` 15m | `seasonInPlay` | 45m |
| `MatchdayAdvancementScheduler` | cron 06:00 | `matchdayAdvancement` | 26h |
| `JoinReminderJob` | cron 09:00 | — | disabled in prod |
| `EmailVerificationTokenCleanupJob` | cron 02:00 | — | low stakes |
| `PasswordResetTokenCleanupJob` | cron 02:00 | — | low stakes |
| `RoundAdvancementService` | one-shot, DB-backed | — | see below |

"Declared max interval" is the longest **legitimate** gap, not the nominal schedule. Match sync
polls every 6h normally but legitimately backs off to 24h when a season is complete, so it declares
30h. `JobHeartbeat` then adds grace on top: `max(interval × 1.5, interval + 5m)`.

Jobs without a heartbeat are a deliberate choice, recorded in `ScheduledJobRegistryTest`'s
`KNOWN_UNMONITORED` map with the reason. The two cleanup jobs are already guarded with Sentry and
their failure means slow bloat on tables of daily-expiring rows — cheaper than an alert path that
will eventually false-fire.

## Rules for a new job

1. **Prefer `@Scheduled(fixedDelay)` or `@Scheduled(cron)`.** Spring re-queues these for you, so
   they cannot stop on their own.
2. **Never hand-roll a self-rescheduling chain.** If you think you need one, read the incident
   below first. `ManualSchedulingAuditTest` pins the two classes allowed to do it.
3. **Guard the whole body.** Use `ScheduledJobRunner.runSafely` (pings a heartbeat) or
   `runGuarded` (no heartbeat). An exception escaping to the pool reaches only the last-resort
   handler in `SchedulingConfig`.
4. **Declare a heartbeat, or declare that you are not.** `ScheduledJobRegistryTest` fails the build
   until you do one or the other.
5. **Use `Sentry.captureException` explicitly.** See the trap below.
6. **`@ConditionalOnProperty` with `matchIfMissing = true`** unless the job must be opt-in. Note the
   trade-off in "Known gaps".

## Trap: `log.error` does not reach Sentry from these packages

`logback-spring.xml` declares `com.ligitabl.api.scheduling` and `com.ligitabl.api.notification.outbox`
with `additivity="false"`, listing only the Console and Logtail appenders. `sentry-logback`
auto-attaches its appender at **root**, which those loggers bypass.

So inside those packages, `log.error(...)` alone reaches Console and Better Stack but **never
Sentry**. Every failure path needs an explicit `Sentry.captureException(e)`. This silently
de-instrumented five failure paths across four files before it was found.

## Trap: the watchdog must not be a `HealthIndicator`

`JobWatchdog` alerts and nothing more. Making it a `HealthIndicator` would aggregate it into
`/actuator/health`, which is what the container healthcheck polls — so a stalled background job
would restart-loop a container that is otherwise serving users perfectly well.

A stalled job is an alert, not an outage. Recovery stays a human decision.

## Monitoring

Three layers, because none is sufficient alone:

1. **Sentry** — catches a job that throws.
2. **`JobWatchdog`** — catches a job that goes quiet without throwing. Scans every 5 min, alerts via
   Slack + admin email with a 6h per-job cooldown, reset on the next successful run.
3. **Better Stack absence alerts** — the only layer that survives the JVM dying. Configured in their
   UI, not in this repo:
   - no `Next sync scheduled for` in **36h** → match sync is stalled
   - no `[JOB_HEARTBEAT_SUMMARY]` in **30min** → the watchdog itself is dead

⚠️ Better Stack shipping depends on `LOGTAIL_TOKEN` being set on the server.
`application-prod.yml` defaults it to empty, so log shipping is silently a no-op if it is missing.
Confirm logs are arriving before relying on either rule.

Scheduling logs ship at **INFO** (root ships at WARN), which is what makes absence alerting on an
INFO heartbeat line possible.

## The incident this came from

On **2026-09-06 17:30:29** match sync stopped and stayed stopped for five days. Found by noticing
the `pl-admin` Slack channel had gone quiet — not by any alarm.

`MatchSyncScheduler` re-arms itself. When all matches completed, `SyncFrequencyCalculator` returned
`Duration.ZERO`, so the next run was scheduled at the *same instant*; it fired on another pool
thread while the current run was still inside `result.fold(...)`, hit the `if (running)` guard, and
**returned without re-arming**. The original run then cleared `running`, but the only queued task had
already discarded itself. The chain was orphaned permanently — only a restart recovered it.

Nothing noticed because the pool's `ErrorHandler` wrote to `System.err` (reaching neither Logback nor
Sentry), nothing asserted "a sync should have happened by now", and the healthcheck only tested
Tomcat, which was serving pages fine the whole time.

Full write-up, including the four wrong hypotheses before the logs settled it: `.art/task_89.md`.

Regression guards: `MatchSyncSchedulerTest#reentrantSkip_stillSchedulesNextSync` (behavioural),
`ManualSchedulingAuditTest` (stops a third self-arming site appearing unexamined).

## Known gaps

- **A job silently disabled by an unset env var is not detected.** `JoinReminderJob` has
  `havingValue="true"` with no `matchIfMissing`, and `JOIN_REMINDER_ENABLED` defaults to `false` —
  so an unset var makes the bean vanish with no log line. `JobHeartbeat` correctly does not alert on
  a job that never registered (a deliberately disabled job is not a sick job), which leaves
  "disabled by accident" as a separate question. A `@SpringBootTest` wiring assertion was considered
  and skipped: it needs a distinct Spring context, and `AbstractPostgresIT` documents the connection
  pressure that caused.
- **No post-deploy smoke test.** `deploy.yml` cannot fail on a broken deploy — its verification is
  `sleep 15`, an unasserted `docker compose ps`, and a `SELECT` whose failure is swallowed.
