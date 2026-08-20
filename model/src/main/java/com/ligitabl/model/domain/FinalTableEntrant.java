package com.ligitabl.model.domain;

/**
 * A player who has a Final Table entry for a season, identified only well enough to name and link
 * them.
 *
 * <p>Deliberately narrower than {@link FinalTableLeaderboardEntry}: this is what the leaderboard can
 * show <em>before</em> anything is scored, so it carries no score, no position and — most
 * importantly — no rankings. The predicted table is the thing the season is waiting on, and a
 * projection that cannot hold it cannot leak it.
 *
 * @param publicId User's unique public identifier, for building their public final-table link
 * @param displayName User's display name; nullable in the column, so callers clean it before
 *     rendering
 */
public record FinalTableEntrant(String publicId, String displayName) {}
