# Leaderboard persistence

This document explains how the leaderboard is assembled inside the persistence layer and how pagination, user context, and movement are computed.

## Primary entry point

The core implementation is in [model/src/main/java/com/ligitabl/model/infra/LeaderboardPersistenceAdapter.java](model/src/main/java/com/ligitabl/model/infra/LeaderboardPersistenceAdapter.java). The main method is `computeLeaderboard()`, which returns a `LeaderboardResponse` that includes:

- `entries`: the paginated list of `LeaderboardEntry` records
- `userEntry`: the current user’s ranking info (if a `userId` is supplied and the user has results)
- `userInCurrentPage`: whether the user appears in the requested page
- `userPageOffset`: the offset for the page that contains the user
- `totalParticipants`: total ranked users in the contest for the round span
- `hasNext` / `hasPrevious`: pagination flags

## High-level flow

1. **Validate inputs**
   - `contestId` and `seasonId` must be non-null.
   - `fromRound` must be positive; `toRound` must be greater than or equal to `fromRound`.
   - `offset` must be non-negative.
   - `limit` must be positive and must not exceed 100.

2. **Resolve effective round range**
   - `resolveEffectiveToRound()` queries `t_round` for the maximum finalized round within the requested span.
   - If no finalized round exists, the leaderboard is empty.

3. **Count participants**
   - `countParticipants()` counts distinct users in the contest who have results in the round span.
   - If the count is zero, the leaderboard is empty.

4. **Fetch current page**
   - `fetchPaginatedRankings()` builds the ranked list and slices it with `offset` and `limit`.

5. **Fetch user ranking (optional)**
   - If a `userId` is provided, `fetchUserRanking()` returns the user’s row, even if it is outside the requested page.
   - `userInCurrentPage` and `userPageOffset` are computed from the user’s position and requested page size.

6. **Fetch previous positions**
   - When possible, `fetchPreviousPositions()` builds a ranking for the previous round span and captures each user’s previous position.
   - Movement is calculated as: `previousPosition - currentPosition`.

7. **Build response**
   - Each `RankingWithPosition` is mapped into a `LeaderboardEntry` with a movement value.
   - Pagination flags are derived from `offset`, `limit`, and `totalParticipants`.

## Aggregation and ranking model

### Aggregated metrics

All scoring columns are derived from `t_round_result` joined through `t_round_submission` and `t_entry`, grouped per user:

- `total_score`: sum of `c_score` across the span
- `round_score`: sum of scores in the final round of the span
- `max_score`: max `c_score` in the span
- `total_zeroes`: sum of `c_zeroes_count` in the span
- `total_swaps`: sum of `c_swap_count` in the span

`coalesce(..., 0)` is used to ensure non-null numeric values.

### CTE: user_stats

Both `fetchPaginatedRankings()` and `fetchUserRanking()` build a common table expression (CTE) named `user_stats`:

- It selects the user identity (`pk_id`, `c_public_id`, `c_display_name`).
- It aggregates the scoring columns for each user.
- It groups by user identity to produce one row per user.

The CTE is used to ensure consistent aggregation and sorting across queries.

### Ranking order (tie-breakers)

Positions are determined by a `row_number()` window function ordered by:

1. `total_score` descending
2. `total_zeroes` descending
3. `total_swaps` ascending (fewer is better)
4. `max_score` descending
5. `public_id` ascending

This ordering is used consistently for both page results and user lookups.

## Pagination mechanics

`fetchPaginatedRankings()` applies `limit` and `offset` directly after the ranking projection:

- `limit` is capped at 100 for UI-friendly pages.
- `hasNext` is computed as `offset + limit < totalParticipants`.
- `hasPrevious` is computed as `offset > 0`.

## User ranking lookup

`fetchUserRanking()` uses a derived table named `ranked_stats` to compute row numbers before filtering by `userId`.

This is important because filtering the CTE first would cause `row_number()` to evaluate against a single-row set. The derived table ensures the window function is computed over the full ordered leaderboard, then the user row is selected.

## Movement calculation

`fetchPreviousPositions()` computes the ranking for the previous round span using the same ordering rules and produces a map from `user_id` to position.

Movement in `buildEntry()` is calculated as:

$$
\text{movement} = \text{previousPosition} - \text{currentPosition}
$$

If a user did not appear in the previous span, movement defaults to 0.

## Related structures

- `LeaderboardResponse`: [model/src/main/java/com/ligitabl/model/domain/LeaderboardResponse.java](model/src/main/java/com/ligitabl/model/domain/LeaderboardResponse.java)
- `LeaderboardEntry`: [model/src/main/java/com/ligitabl/model/domain/LeaderboardEntry.java](model/src/main/java/com/ligitabl/model/domain/LeaderboardEntry.java)
- `LeaderboardRepo`: [model/src/main/java/com/ligitabl/model/repo/LeaderboardRepo.java](model/src/main/java/com/ligitabl/model/repo/LeaderboardRepo.java)
