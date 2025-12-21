package com.ligitabl.seed.internal.config;

import com.ligitabl.seed.internal.util.SeedCoercions;
import java.time.LocalDate;
import java.util.Map;

import static com.ligitabl.seed.internal.util.SeedCoercions.*;

public class MatchSeedConfig {

    private final String competitionSlug;
    private final String seasonSlug;
    private final int clientId;
    private final String scheduledStatus;
    private final String finishedStatus;
    private final int finishedRounds;
    private final Long randomSeed;
    private final LocalDate seasonStartDate;

    private MatchSeedConfig(Builder builder) {
        this.competitionSlug = builder.competitionSlug;
        this.seasonSlug = builder.seasonSlug;
        this.clientId = builder.clientId;
        this.scheduledStatus = builder.scheduledStatus;
        this.finishedStatus = builder.finishedStatus;
        this.finishedRounds = builder.finishedRounds;
        this.randomSeed = builder.randomSeed;
        this.seasonStartDate = builder.seasonStartDate;
    }

    public String getCompetitionSlug() { return competitionSlug; }
    public String getSeasonSlug() { return seasonSlug; }
    public int getClientId() { return clientId; }
    public String getScheduledStatus() { return scheduledStatus; }
    public String getFinishedStatus() { return finishedStatus; }
    public int getFinishedRounds() { return finishedRounds; }
    public Long getRandomSeed() { return randomSeed; }
    public LocalDate getSeasonStartDate() { return seasonStartDate; }

    public String getStatusForRound(int roundPosition) {
        return roundPosition <= finishedRounds ? finishedStatus : scheduledStatus;
    }

    public boolean shouldHaveScore(int roundPosition) {
        return roundPosition <= finishedRounds;
    }

    @SuppressWarnings("unchecked")
    public static MatchSeedConfig fromMap(Map<String, Object> map) {
        return new Builder()
                .competitionSlug(asString(map.get("competitionSlug")))
                .seasonSlug(asString(map.get("seasonSlug")))
                .clientId(asInt(map.getOrDefault("clientId", 1)))
                .scheduledStatus(asString(map.getOrDefault("scheduledStatus", "SCHEDULED")))
                .finishedStatus(asString(map.getOrDefault("finishedStatus", "FINISHED")))
                .finishedRounds(asInt(map.getOrDefault("finishedRounds", 0)))
                .randomSeed(asLong(map.get("randomSeed")))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String competitionSlug;
        private String seasonSlug;
        private int clientId = 1;
        private String scheduledStatus = "SCHEDULED";
        private String finishedStatus = "FINISHED";
        private int finishedRounds = 0;
        private Long randomSeed = null;
        private LocalDate seasonStartDate;

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

        public Builder scheduledStatus(String scheduledStatus) {
            this.scheduledStatus = scheduledStatus;
            return this;
        }

        public Builder finishedStatus(String finishedStatus) {
            this.finishedStatus = finishedStatus;
            return this;
        }

        public Builder finishedRounds(int finishedRounds) {
            this.finishedRounds = finishedRounds;
            return this;
        }

        public Builder randomSeed(Long randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder seasonStartDate(LocalDate seasonStartDate) {
            this.seasonStartDate = seasonStartDate;
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

