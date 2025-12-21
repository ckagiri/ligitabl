package com.ligitabl.seed.internal.config;

import com.ligitabl.seed.internal.util.SeedCoercions;
import java.util.Map;

import static com.ligitabl.seed.internal.util.SeedCoercions.asInt;
import static com.ligitabl.seed.internal.util.SeedCoercions.asString;

/**
 * Type-safe configuration for round seeding.
 */
public class RoundSeedConfig {

    private final String competitionSlug;
    private final String seasonSlug;
    private final int count;
    private final String namePrefix;
    private final String slugPrefix;
    private final int startPosition;

    private RoundSeedConfig(Builder builder) {
        this.competitionSlug = builder.competitionSlug;
        this.seasonSlug = builder.seasonSlug;
        this.count = builder.count;
        this.namePrefix = builder.namePrefix;
        this.slugPrefix = builder.slugPrefix;
        this.startPosition = builder.startPosition;
    }

    public String getCompetitionSlug() {
        return competitionSlug;
    }

    public String getSeasonSlug() {
        return seasonSlug;
    }

    public int getCount() {
        return count;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public String getSlugPrefix() {
        return slugPrefix;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public static RoundSeedConfig fromMap(Map<String, Object> map) {
        return new Builder()
                .competitionSlug(asString(map.get("competitionSlug")))
                .seasonSlug(asString(map.get("seasonSlug")))
                .count(asInt(map.getOrDefault("count", 0)))
                .namePrefix(asString(map.getOrDefault("namePrefix", "Round ")))
                .slugPrefix(asString(map.getOrDefault("slugPrefix", "gw-")))
                .startPosition(asInt(map.getOrDefault("startPosition", 1)))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String competitionSlug;
        private String seasonSlug;
        private int count;
        private String namePrefix = "Round ";
        private String slugPrefix = "round-";
        private int startPosition = 1;

        public Builder competitionSlug(String competitionSlug) {
            this.competitionSlug = competitionSlug;
            return this;
        }

        public Builder seasonSlug(String seasonSlug) {
            this.seasonSlug = seasonSlug;
            return this;
        }

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder namePrefix(String namePrefix) {
            this.namePrefix = namePrefix;
            return this;
        }

        public Builder slugPrefix(String slugPrefix) {
            this.slugPrefix = slugPrefix;
            return this;
        }

        public Builder startPosition(int startPosition) {
            this.startPosition = startPosition;
            return this;
        }

        public RoundSeedConfig build() {
            if (competitionSlug == null || competitionSlug.isBlank()) {
                throw new IllegalArgumentException("competitionSlug is required");
            }
            if (seasonSlug == null || seasonSlug.isBlank()) {
                throw new IllegalArgumentException("seasonSlug is required");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive");
            }
            return new RoundSeedConfig(this);
        }
    }
}

