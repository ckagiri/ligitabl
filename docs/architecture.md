# Architecture

How the backend is layered, which dependency rules hold, how they're enforced, and where the code
still falls short of them. Read this before adding a use case, a port, or a new adapter package.

The shape is clean architecture kept light. Use cases are the core, adapters depend on them, and
ports exist only where the core meets the outside world and translates its types. Most of the
value comes from one rule held strictly, dependency direction, rather than from layers of
interfaces.

## Modules

- **`model`**: the domain and its persistence.
  - `domain`: entities, value objects and pure rules such as `Season`'s state predicates and the `StandingsCalculator`. It imports no Spring, jOOQ or `infra`.
  - `repo`: repository interfaces. These are the persistence ports, so `api` has no second layer of them.
  - `infra`: the jOOQ implementations. `db` holds the generated jOOQ code.
  - In `api`, only `config/RepositoryConfig` touches `infra`, to wire it.
- **`api`**: the application server. It holds the use cases plus every adapter: JSON and Thymeleaf controllers, schedulers, runners, notifications and auth.

## Package layout (api module)

- `usecase/<feature>/<slice>/`: one use case plus its Command/Query/Result/Error types, or the few use cases of one small flow (`auth/passwordreset`: request + reset).
  - Helpers and DTOs shared by a feature's slices go in `usecase/<feature>/shared/`. Ones shared across features go in `usecase/shared/`.
  - Nothing sits directly in `usecase/<feature>/`.
  - A use case is a `*UseCase` class whose public entry point is `execute`. When one use case has two triggers, overload `execute` by input type, as `EnqueueSegmentResultsEmailsUseCase` does with a round payload and a season-completed payload.
- `rest/<feature>/`: JSON controllers and HTTP-only DTOs.
- `web/…`: Thymeleaf controllers and view helpers.
- `scheduling/…`: schedulers that trigger use cases.
- `runners/…`: self-contained CLI tools (importer, demo seeding, standings calc). These keep their own use cases.
- Outbound adapters: `client` (football-data, Turnstile), `notification` (Slack, email, outbox), `auth` (Spring Security, OAuth2, impersonation).
- `config`, `shared` (`Either`, `UseCase`, error types), `logging`.

Use case input and output types, and when to group or build large records, are covered in [Backend Development Guide](./backend-dev.md).

## Dependency rules

**1. Adapters depend on `usecase`, never the reverse.**
This covers `rest`, `web`, `scheduling` and `runners`. If a use case needs something from an adapter package, move that class into `usecase`.

**2. Writes and business rules go through a use case. Reads for a view don't have to.**
An inbound adapter may read a repo directly to assemble a view: a lookup for a page header, a dropdown, a redirect target. Any write, and any business rule, goes through a use case however small it is. "Unlink Google only if a password is set" is a rule even when the write is a single `userRepo.unlinkGoogle`.

A rule that depends only on one entity's state belongs on the entity, as with `User.canUnlinkGoogle()`. The use case checks it before writing, and the view reads it to decide what to show, so the two can't drift. A use case earns its own class when there is something to do: a write, a transaction, or more than one repo or port to coordinate. A view that only asks the entity a question doesn't need one.

**3. Outbound adapters are reached through ports.**
This covers `client`, `notification` and `auth`. The use case depends on an interface and the adapter implements it. The port and the types in its signature live in `usecase` (shared ones in `usecase/shared/…`), so the dependency points from the adapter to the core.
- Examples: `MatchFeed`, `AdminAlerts`, `TokenGenerator`, `EmailProvider`, `EmailTemplateRenderer`.
- Outbox event types, and the payloads use cases write (`usecase/shared/outbox`), follow the same rule.

**4. A port belongs where the core meets the outside world and translates its types.**
`MatchFeed` returns `FeedMatch` records with our `MatchStatus`, never football-data's DTOs or status strings. Don't put an interface between two adapters: a runner or scheduler can use `client.*` directly. (This is why the importer's `FootballDataGateway` was removed.)

**5. Transactions belong on the use case.**
A scheduler or controller that needs one is holding logic that belongs in a use case. This also sidesteps the self-invocation trap described in [Background Jobs](./background-jobs.md).

## How the rules are enforced

| Rule | Enforced by | Strength |
|---|---|---|
| 1. `usecase` doesn't reference `rest`, `web`, `scheduling`, `runners` | `UseCaseDependencyRuleTest` | Build fails |
| 3. `usecase` doesn't reference `client`, `notification`, `auth` | `UseCaseDependencyRuleTest` | Build fails |
| Every `@Scheduled` method is monitored or exempt | `ScheduledJobRegistryTest`, `ManualSchedulingAuditTest` | Build fails |
| Every `*UseCase` has an `execute` entry point | `UseCaseEntryPointRuleTest` (#286) | Build fails |
| `notification` and `client` don't import `model.repo` | `OutboundAdapterRuleTest` (#286) | Build fails |
| 2. No writes or rules in adapters | Review | None |
| 4, 5 | Review | None |

⚠️ `UseCaseDependencyRuleTest` matches **source text** against regexes built from package names. A literal that has gone stale matches nothing, so the test passes no matter what the code does. After moving or renaming a package, prove that each rule still fails: add a violating import, watch the test go red, then remove it.

## Where the code stands (2026-10-29)

**What holds:**
- `usecase` has no references to any adapter package, and the build enforces it.
- `model`'s `domain` and `repo` are free of framework and persistence imports.
- No use case nests its public contract types. Results and errors are sealed, so `switch` is exhaustive.
- Expected failures are `Either` values rather than exceptions. Newer code takes an injected `Clock`.
- Use case tests are cheap because of all the above.

**Where it's weaker:**
- **The leaks are at the edges, in adapters that have repo access.** Every business rule found outside `usecase` sat in one. Most have moved into use cases: the season auto-activation scheduler (#280), the account and Google sign-in rules (gaps 1a and 1b), and the outbox enqueuers (gap 2). One remains: the auto-join cohorts in `OutboxEventProcessor` (gap 8).

  The test guards the core from adapters, but nothing yet stops an adapter from turning into a use case.
- **Rule 2 relies on review alone,** and a review-only rule erodes. Gap 4 records what an audit found.
- **Shared helpers are where coupling builds up.** `usecase` has 16 helper classes (`*Support`, `*Service`, `*Helper`, `*Enricher`) beside 90 use cases. The use-case count was 77 on 2026-10-28; the helper count hasn't moved. `*Support` especially tends to become the place logic goes when nobody wants to write a use case. Prefer a use case or a domain method.

## Known gaps

| # | Gap | Fix | When |
|---|---|---|---|
| 1a | Profile update, set-password and unlink-Google rules live in `ProfileController` and `ConnectedAccountsController`. "Record last login" is copied in 4 places (`AuthController`, `OAuth2AuthenticationSuccessHandler`, `PlayerImpersonationService`, `RememberMeLoginListener`), each with `OffsetDateTime.now()`. | Account use cases, plus one `RecordLoginUseCase` that takes a `Clock`. | Done in #284 (also fixes set-password not saving the password) |
| 1b | Google sign-in rules live in `auth/oauth2`:<br>• `CustomOAuth2UserService` links an existing user by email, or creates the user and sends the signup alert through the concrete `AdminNotificationService`, so it duplicates part of `RegisterUseCase`.<br>• `OAuth2AuthenticationSuccessHandler` links Google from the profile page.<br>• Web login checks credentials inline in `AuthController` rather than sharing `LoginUseCase`'s check. That check has no guard for a user without a password: `BCryptPasswordHasher.verify` dereferences the null hash. | Google sign-in, Google link and credential-check use cases. The shared credential check guards a missing password. | Done in #285 |
| 2 | The 5 `notification/outbox/*Enqueuer`s decide who is emailed and write outbox rows, called straight from `scheduling`. `MatchSyncScheduler` writes the `ROUND_LOCKED` event itself. | Move the rules into use cases, so the jobs become triggers. Then add a rule: `notification` and `client` don't import `model.repo`. | Done in #286 (adds `OutboundAdapterRuleTest`) |
| 3 | The dependency rules match text, not real class dependencies. | Replace with ArchUnit, test scope only. | After the rename |
| 4 | Rule 2 is unenforced. An audit on 2026-10-29 covered `web`, `rest`, `auth`, `scheduling`, `notification` and `client`. About 25 classes import a repo, and nearly all only read for a view. The repo writes are:<br>• outbox delivery bookkeeping in `OutboxRelayJob` and `OutboxEventProcessor` (`claimBatchForProcessing`, `mark*`, `resetStuckProcessing`): the outbox mechanism itself;<br>• `deleteExpired` in the two token cleanup jobs: housekeeping with no rule;<br>• `predictionRepo.clearResults` in `DevFinalTableController`: dev only;<br>• `outboxRepo.save` of the welcome fan-out in `OutboxEventProcessor`: part of gap 8.<br>A rule hidden inside a read, like gap 8's cohort queries, can't be found by grep or ArchUnit and stays review-only. | An ArchUnit rule that adapters don't call repo write methods. Once gap 8 is done it starts with no violations, only the exemptions above, named. | After the rename |
| 5 | `CompetitionDefaults`, a plain record that 28 use cases import, lives in `config`. | None needed. Move it if `config` ever gains a rule. | — |
| 6 | An unexpected exception in a JSON endpoint returns its raw message in the 500 body. Found via #285: `Cannot invoke "…Password$Hashed.value()" because "hashed" is null`. | Return a generic message, and keep the detail in logs and Sentry. The body and an `X-Request-Id` header carry the request ID, which links the response to the log line. | Done in #287 |
| 7 | `UnexpectedError.getMessage()` was the cause's message, and about 12 web controllers put `error.getMessage()` straight into the model. | `getMessage()` is now always safe to show; logs use `UseCaseError.logMessage()`. `UseCaseException` carries the real cause. | Done in #287 |
| 8 | `scheduling/outbox/OutboxEventProcessor` auto-joins users itself, inside its `@Transactional processOne`. It does this in two handlers:<br>• `ROUND_LOCKED` picks the cohort with `userRepo.findUnjoinedUserIdsAfter` and joins each user into the locked round;<br>• `SEASON_IN_PLAY` picks it with `findUnjoinedUserIdsActiveSince`, gives each user a default-table registration, and then writes the welcome fan-out event.<br>Each handler loops over `CreatePredictionUseCase`. That puts a rule in an adapter (rule 2) and a transaction outside a use case (rule 5). | Move both into a use case, so the processor only dispatches. Keep the processor's failure contract: database errors are rethrown, and `markFailed` is deliberately not `@Transactional`. | Before the rename |
