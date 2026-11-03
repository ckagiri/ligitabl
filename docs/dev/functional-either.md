# Functional `Either` in LigiTabl

This project uses a right-biased `Either<L, R>` (`api/src/main/java/com/ligitabl/api/shared/Either.java`) for explicit, typed error handling and fluent composition.

Convention:

- `Left` = error/failure value
- `Right` = success value

## Why `Either`?

- Makes error flows explicit in types (no hidden exceptions)
- Encourages composition (`map`/`flatMap`) and predictable control flow
- Keeps domain errors (Left) separate from success values (Right)

## Quick reference (core API)

### Construction

- `Either.left(L)` / `Either.right(R)`
- `Either.ofNullable(R, L)` / `Either.ofOptional(Optional<R>, L)` (and supplier overloads)
- `Either.combine(...)` fail-fast combine (returns `Right(Unit)` when all succeed)

### Transform / compose (right-biased)

- `map`, `flatMap`
- `mapLeft`, `bimap`, `swap`
- `filterOrElse(predicate, leftSupplier)` and `filter(predicate, errorProvider)`

### Recover

- `getOrElse(value)` / `getOrElse(supplier)`
- `getOrElseMap(left -> right)`
- `getOrElseThrow(left -> exception)`
- `orElse(other)` / `orElse(supplier)`
- `recover(left -> right)` / `recoverWith(left -> Either)`

### Observe / log

- `peek(right -> void)`, `peekLeft(left -> void)`, `peekBoth(left -> void, right -> void)`
- `peekIf(predicate, action)`
- Logging helpers: `logLeft`, `logLeftWithException`, `logRight`

### Convert / pattern-match

- `toOptional()`, `toOptionalLeft()`, `toOptional(leftMapper)`
- `fold(leftMapper, rightMapper)`
- `foldWithContext(context, leftBiMapper, rightBiMapper)`

## Catching exceptions into `Either` (recommended)

The implementation provides a single, consistent “catching” API. Pick the variant based on how broad you want the catch to be. See "`Left` or an exception?" below for when to catch at all.

### `Either.catching`

- Wrap one call that throws on bad input, such as `Email.create`. Never wrap a repo call or a whole use case.
- Catches `Exception` (checked + runtime), but rethrows `Error`.
- Preserves interrupts: if the thrown error is an `InterruptedException`, it re-sets the thread interrupt flag.

Examples:

```java
// Supplier form (Right on success, Left on mapped Exception)
Either<UseCaseError, Email> email = Either.catching(() -> Email.create(request.email()), UseCaseErrors::fromException);

// Function form (use in flatMap chains)
return either.flatMap(Either.catching(value -> mapper.map(value), UseCaseErrors::fromException));
```

### `Either.catchingAll` (use sparingly)

- Catches _everything_, including `Error`.
- Only use at process boundaries where you truly want to isolate failures.

```java
Either<Throwable, Void> result = Either.catchingAll(() -> {
    runBackgroundJob();
    return null;
});
```

### `Either.catchingChecked` (fail-fast on bugs)

- Catches _only checked_ exceptions.
- Runtime exceptions and `Error` propagate (fail fast).

```java
Either<Exception, String> text = Either.catchingChecked(() -> Files.readString(path));
```

## Examples

### Validation + mapping

```java
return requestValidator.validate(cmd)
    .flatMap(valid -> Either.catching(() -> mapper.toEntity(valid), UseCaseErrors::fromException))
    .flatMap(service::save);
```

### Branching without `if` chains

```java
return result.fold(
    error -> ResponseEntity.badRequest().body(Map.of("message", error.getMessage())),
    ok -> ResponseEntity.ok(ok)
);
```

### Log errors but keep type-safety

```java
return useCase.execute(cmd)
    .logLeft("Register", log)
    .getOrElseThrow(UseCaseException::new);
```

## `Left` or an exception?

- **`Left`**: a failure the caller handles. That means bad input, a broken business rule, or an infrastructure failure the business process has an answer to, such as the football feed being down.
- **Exception**: a bug, or an infrastructure failure nobody can act on, such as a failed query. Let it reach the error handlers, which log it at ERROR and return a generic 500.

Sealed error types get no catch-all variant for exceptions, such as `Failed(String reason)`: it drops the stack trace and hides the failure from the error handlers.

**A `Left` from a `@Transactional` method commits.** Only an exception leaving the proxy rolls back. Don't catch exceptions around writes, and check business rules before the first write. If a `Left` must follow a write, call `setRollbackOnly`. Use cases running inside the outbox processor's transaction are the exception: see "Transactions belong on the use case" in [Architecture](../architecture.md).

## `UseCaseError` or a per-use-case type?

- **`UseCaseError`**: every caller only picks a status and shows the message. CRUD and thin reads usually stay here.
- **A sealed type for the use case**, such as `SwapError`: a caller does anything more, by branching on the specific failure, showing data carried by the error, or taking a different path. That caller may be the one you're writing.

Never branch on `getMessage()` text. Needing to is the signal to move to a sealed type. Existing sealed types stay as they are: this rule decides where new code starts and when to promote, and doesn't call for migrating anything.

`UseCaseErrorStatus` is the one mapping from `UseCaseError` to an HTTP status.

## Best practices (repo conventions)

- Reserve `catchingAll(...)` for true “last chance” boundaries.
- Don’t swallow interrupts: the catching helpers already preserve them.
- Avoid `null` inside `Either` values. Prefer domain objects or a `Unit` right value when you need “success with no payload”.
