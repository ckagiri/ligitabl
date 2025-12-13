package com.ligitabl.seed.internal.config;

import java.util.List;
import java.util.Map;

/**
 * Type-safe configuration for match seeding.
 */
public class MatchSeedConfig {

    private final String competitionSlug;
    private final String seasonSlug;
    private final int clientId;
    private final String status;

    private MatchSeedConfig(Builder builder) {
        this.competitionSlug = builder.competitionSlug;
        this.seasonSlug = builder.seasonSlug;
        this.clientId = builder.clientId;
        this.status = builder.status;
    }

    public String getCompetitionSlug() {
        return competitionSlug;
    }

    public String getSeasonSlug() {
        return seasonSlug;
    }

    public int getClientId() {
        return clientId;
    }

    public String getStatus() {
        return status;
    }

    @SuppressWarnings("unchecked")
    public static MatchSeedConfig fromMap(Map<String, Object> map) {
        return new Builder()
                .competitionSlug((String) map.get("competitionSlug"))
                .seasonSlug((String) map.get("seasonSlug"))
                .clientId((Integer) map.getOrDefault("clientId", 1))
                .status((String) map.getOrDefault("status", "SCHEDULED"))
                .build();
    }

    @SuppressWarnings("unchecked")
    public static MatchSeedConfig fromFirstSeason(List<Map<String, Object>> seasons) {
        if (seasons == null || seasons.isEmpty()) {
            throw new IllegalArgumentException("Cannot auto-generate match config: no seasons provided");
        }

        Map<String, Object> firstSeason = seasons.get(0);
        String competitionSlug = (String) firstSeason.get("competitionSlug");
        String seasonSlug = (String) firstSeason.get("slug");

        if (competitionSlug == null || seasonSlug == null) {
            throw new IllegalArgumentException(
                    "Cannot auto-generate match config: first season missing competitionSlug or slug");
        }

        return new Builder()
                .competitionSlug(competitionSlug)
                .seasonSlug(seasonSlug)
                .clientId(1)
                .status("SCHEDULED")
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String competitionSlug;
        private String seasonSlug;
        private int clientId = 1;
        private String status = "SCHEDULED";

        public Builder competitionSlug(String competitionSlug) {
            this.competitionSlug = competitionSlug;
            return this;
        }

        public Builder seasonSlug(String seasonSlug) {
            this.seasonSlug = seasonSlug;
            return this;
        }

        public Builder clientId(int clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public MatchSeedConfig build() {
            if (competitionSlug == null || competitionSlug.isBlank()) {
                throw new IllegalArgumentException("competitionSlug is required");
            }
            if (seasonSlug == null || seasonSlug.isBlank()) {
                throw new IllegalArgumentException("seasonSlug is required");
            }
            return new MatchSeedConfig(this);
        }
    }
}
