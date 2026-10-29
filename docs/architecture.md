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
  - A use case is a `*UseCase` class whose only public methods are `execute` overloads. When one use case has more than one trigger, each gets an `execute` overload with its own input type: `EnqueueSegmentResultsEmailsUseCase` takes a round payload or a season-completed payload, and `CreatePredictionUseCase` takes a user's `CreatePredictionCommand`, a `JoinLockedRoundCommand` or a `RegisterDefaultTableCommand`.
  - Contract types (commands, queries, results, errors) are top-level files in the slice, never nested in the use case.
- `rest/<feature>/`: JSON controllers and HTTP-only DTOs.
- `web/…`: Thymeleaf controllers and view helpers.
- `scheduling/…`: schedulers that trigger use cases.
- `runners/…`: self-contained CLI tools (importer, demo seeding, standings calc). These keep their own use cases.
- Outbound adapters: `client` (football-data, Turnstile), `notification` (Slack, email, outbox), `auth` (Spring Security, OAuth2, impersonation).
- `config`, `shared` (`Either`, `UseCase`, error types), `logging`.
  - `CompetitionDefaults` is a plain record that 26 use cases import, yet it lives in `config`. That's fine while `config` has no rule. Move it if `config` ever gains one.

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
`MatchFeed` returns `FeedMatch` records with our `MatchStatus`, never football-data's DTOs or status strings. Don't put an interface between two adapters: a runner or scheduler uses `client.*` directly, so the importer calls the football-data client with no gateway interface in between.

**5. Transactions belong on the use case.**
A scheduler or controller that needs one is holding logic that belongs in a use case. This also sidesteps the self-invocation trap described in [Background Jobs](./background-jobs.md).

The outbox is the exception. `OutboxEventProcessor.processOne` holds one transaction per event, so a handler's work and the event's `markSent` commit together. The use cases it calls don't declare `@Transactional`: they run inside that transaction. A proxy of their own would mark the shared transaction rollback-only on any runtime exception, which bypasses the processor's `markFailed`.

## How the rules are enforced

| Rule | Enforced by | Strength |
|---|---|---|
| 1. `usecase` doesn't reference `rest`, `web`, `scheduling`, `runners` | `UseCaseDependencyRuleTest` | Build fails |
| 3. `usecase` doesn't reference `client`, `notification`, `auth` | `UseCaseDependencyRuleTest` | Build fails |
| Every `*UseCase` has an `execute` method and no other public methods or nested public types | `UseCaseEntryPointRuleTest` | Build fails |
| `notification` and `client` don't import `model.repo` | `OutboundAdapterRuleTest` | Build fails |
| Every `@Scheduled` method is monitored or exempt | `ScheduledJobRegistryTest`, `ManualSchedulingAuditTest` | Build fails |
| 2. No writes or rules in adapters | Review | None |
| 4, 5 | Review | None |

⚠️ `UseCaseDependencyRuleTest` and `UseCaseEntryPointRuleTest` match **source text** against regexes. A literal that has gone stale, such as a package name after a rename, matches nothing, so the test passes no matter what the code does. After moving or renaming a package, prove that each rule still fails: add a violation, watch the test go red, then remove it.

## Current state

**What holds:**
- `usecase` has no references to any adapter package, and the build enforces it.
- `model`'s `domain` and `repo` are free of framework and persistence imports.
- Use cases expose only `execute` overloads, and their contract types are top-level.
- Error types are sealed, and so is every result type that models more than one outcome, so `switch` is exhaustive. A result that only carries success data is a record or a `@Value` class.
- Expected failures are `Either` values rather than exceptions. Use cases read the time from an injected `Clock`.
- Use case tests are cheap because of all the above.

**Where it's weaker:**
- **Business rules leak at the edges.** Adapters with repo access are where rules creep in outside `usecase`: a scheduler choosing who to email, a controller checking account state before a write. The build guards the core from adapters, but nothing yet stops an adapter from growing a rule of its own (gap 2).
- **Rule 2 relies on review alone,** and a review-only rule erodes.
- **Shared helpers are where coupling builds up.** `usecase` has 16 helper classes (`*Support`, `*Service`, `*Helper`, `*Enricher`) beside 91 use cases. `*Support` especially tends to become the place logic goes when nobody wants to write a use case. Prefer a use case or a domain method.

## Known gaps

| # | Gap | Fix |
|---|---|---|
| 1 | The dependency and entry-point rules match source text, not real class dependencies. | Replace them with ArchUnit, test scope only. |
| 2 | Rule 2 is unenforced. 37 adapter classes import a repo (`web` 21, `auth` 8, `scheduling` 6, `rest` 2), and nearly all only read for a view. The only repo writes in adapters are:<br>• outbox delivery bookkeeping in `OutboxRelayJob` and `OutboxEventProcessor` (`claimBatchForProcessing`, `mark*`, `resetStuckProcessing`): the outbox mechanism itself;<br>• `deleteExpired` in the two token cleanup jobs: housekeeping with no rule;<br>• `predictionRepo.clearResults` in `DevFinalTableController`: dev only.<br>A rule hidden inside a read, such as choosing which users a batch applies to, can't be found by grep or ArchUnit and stays review-only. | An ArchUnit rule that adapters don't call repo write methods, with the three exemptions above named. It starts with no violations. |
