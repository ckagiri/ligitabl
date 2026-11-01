package com.ligitabl.model.query;

/**
 * A player who has a Final Table entry for a season, identified only well enough to name and link
 * them.
 *
 * <p>Carries no score, position or rankings, unlike {@link FinalTableLeaderboardEntry}: it is what
 * the leaderboard shows before scoring, and a projection that cannot hold the predicted table
 * cannot leak it.
 *
 * @param publicId for building the user's public final-table link
 * @param displayName nullable in the column, so callers clean it before rendering
 */
public record FinalTableEntrant(String publicId, String displayName) {}
