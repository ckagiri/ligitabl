# Functional Either in LigiTabl

This project uses a right-biased `Either<L, R>` for clear, typed error handling and fluent composition.

## Why Either?

- Makes error flows explicit in types (no hidden exceptions)
- Encourages composition (map/flatMap) and predictable control flow
- Keeps domain errors (Left) separate from success values (Right)

## Quick reference

- Factories:
  - `Either.left(L)` / `Either.right(R)`
  - `Either.ofNullable(R, L)` / `Either.ofOptional(Optional<R>, L)`
  - `Either.combine(List<Either<L, ?>>)` and varargs: fail-fast, returns `Right(Unit)` when all succeed
- Core ops (right-biased):
  - `map`, `flatMap`, `mapLeft`, `bimap`, `swap`
- Recovery:
  - `getOrElse`, `orElse`, `recover(L -> R)`, `recoverWith(L -> Either<L,R>)`
- Side-effects:
  - `peek(R -> void)`, `peekLeft(L -> void)`, `peekBoth(L -> void, R -> void)`, `peekIf(Predicate<R>, Consumer<R>)`
  - Logging helpers: `logLeft`, `logLeftWithException`, `logRight`
- Conversion:
  - `toOptional()`, `toOptionalLeft()`, and `toOptional(leftMapper)`

## Lifting throwing code

Pick ONE of these families depending on your needs:

- Recommended default (catch Exception, rethrow Error):

  - `liftException(CheckedFunction<R,T>, Function<Exception, L>)`
  - `liftException(CheckedFunction<R,T>) // Left = Exception`
  - `liftException(CheckedFunction<R,T>, L) // fixed Left`
  - `fromException(CheckedSupplier<T>, Function<Exception, L>)` and overloads
  - Behavior: preserves `InterruptedException` by re-setting the thread interrupt flag.

- Catch-all boundary (catch Throwable, including Error) — use sparingly at process edges:

  - `liftThrowable(CheckedFunction<R,T>, Function<Throwable, L>)` (identity overload available)
  - `fromThrowable(CheckedSupplier<T>, Function<Throwable, L>)` (identity overload available)

- Fail-fast checked-only (only catch checked Exception; runtime exceptions and Error propagate):
  - `liftChecked(CheckedOnlyFunction<R,T>, ...)`
  - `fromChecked(CheckedOnlySupplier<T>, ...)`

Notes:

- `CheckedFunction` and `CheckedSupplier` may throw any `Throwable`.
- `InterruptedException` is handled specially: the interrupt flag is preserved.

## Examples

- Mapping and validation:

```java
Either<DomainError, User> user = validator.validate(cmd)
    .flatMap(Either.liftException(mapper::toEntity, DomainError::fromException))
    .flatMap(guards::validate);
```

- Recover with a fallback value:

```java
Either<DomainError, Price> price = pricing.calculate(item)
    .recover(err -> Price.zero());
```

- Log and continue:

```java
either.logLeft("CreateTeam", log)
     .recoverWith(err -> Either.left(err));
```

- Catch-all boundary (e.g., last-chance adapter):

```java
Either<Throwable, Void> result = Either.fromThrowable(() -> {
    runBackgroundJob();
    return null;
});
```

## Best practices

- Prefer mapping exceptions to a small, well-typed domain error (e.g., `UseCaseError`) instead of leaking raw `Exception`/`Throwable`.
  - Use the `errorMapper` overloads or the fixed-left overloads to normalize errors at boundaries.
- Use `liftException/fromException` in application code. Reserve `liftThrowable/fromThrowable` for process boundaries.
- Preserve interrupts: don’t swallow `InterruptedException` — the helpers already re-set the flag.
- Keep `Left` values non-null (enforced by `Left` constructor).
- Testing tips:
  - Assert Right/Left with `isRight()/isLeft()` and then on `get()`/`getLeft()` accordingly.
  - For side-effects, use `AtomicBoolean`/`AtomicReference` to assert consumers were called or not.

## Changelog

- 2025-11-02: Introduced `peekBoth`, `liftThrowable/fromThrowable`. Consolidated lifting helpers and removed deprecated `Try` helpers.
