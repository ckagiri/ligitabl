package com.ligitabl.api.usecases.demoseeding;

import java.util.UUID;

public sealed interface SeedingError {
    record UserNotFound(String email) implements SeedingError {
        @Override
        public String message() {
            return String.format("User not found: '%s'. Please create demo users first.", email);
        }
    }

    record CompetitionNotFound(String slug) implements SeedingError {
        @Override
        public String message() {
            return String.format("Competition not found: '%s'. Please create it first.", slug);
        }
    }

    record SeasonNotFound(String slug) implements SeedingError {
        @Override
        public String message() {
            return String.format("Season not found: '%s'. Please create it first.", slug);
        }
    }

    record RoundsNotFound(String seasonName, int expected, int found) implements SeedingError {
        @Override
        public String message() {
            return String.format(
                    "Expected %d rounds but found %d for season '%s'. Please create all rounds first.",
                    expected, found, seasonName);
        }
    }

    record NoRoundsFound(String seasonName) implements SeedingError {
        @Override
        public String message() {
            return String.format("No rounds found for season '%s'. Please create rounds first.", seasonName);
        }
    }

    record TeamNotFound(String code) implements SeedingError {
        @Override
        public String message() {
            return String.format("Team not found: '%s'. Please create it first.", code);
        }
    }

    record TeamProfileNotFound(String code) implements SeedingError {
        @Override
        public String message() {
            return String.format("Team '%s' does not have a profile in team-ratings.yaml. Please add it.", code);
        }
    }

    record DefaultContestNotFound(UUID contestId) implements SeedingError {
        @Override
        public String message() {
            return String.format(
                    "Default contest with ID %d not found for season. Please check season.main_contest_id.", contestId);
        }
    }

    record ConfigurationError(String details) implements SeedingError {
        @Override
        public String message() {
            return String.format("Configuration error: %s", details);
        }
    }

    record FinalizationFailed(int roundNumber, String reason) implements SeedingError {
        @Override
        public String message() {
            return String.format("Failed to finalize round %d: %s", roundNumber, reason);
        }
    }

    /**
     * Gets human-readable error message.
     */
    String message();
}
