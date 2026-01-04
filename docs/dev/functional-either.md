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

The implementation provides a single, consistent “catching” API. Pick the variant based on how broad you want the catch to be.

### `Either.catching` (recommended default)

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

## Best practices (repo conventions)

- Map exceptions to a small, stable domain error type (e.g., `UseCaseError`) at the boundary; don’t leak raw `Exception`/`Throwable` widely.
- Prefer `Either.catching(...)` in application code; reserve `catchingAll(...)` for true “last chance” boundaries.
- Don’t swallow interrupts: the catching helpers already preserve them.
- Avoid `null` inside `Either` values. Prefer domain objects or a `Unit` right value when you need “success with no payload”.
