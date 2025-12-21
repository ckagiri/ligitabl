package com.ligitabl.seed.internal.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KickoffGenerator {

    private static final LocalTime[] SATURDAY_TIMES = {
            LocalTime.of(12, 30),
            LocalTime.of(15, 0),
            LocalTime.of(17, 30)
    };

    private static final LocalTime[] SUNDAY_TIMES = {
            LocalTime.of(14, 0),
            LocalTime.of(16, 30)
    };

    private final Random random;

    public KickoffGenerator() {
        this.random = new Random();
    }

    public KickoffGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates kickoff times for a round of matches.
     *
     * @param roundPosition Round number (1-based)
     * @param totalMatches Number of matches in the round
     * @param seasonStartDate Season start date
     * @return List of kickoff times
     */
    public List<LocalDateTime> generateKickoffs(
            int roundPosition,
            int totalMatches,
            LocalDate seasonStartDate) {

        // Find the weekend for this round
        LocalDate weekendStart = findWeekendForRound(roundPosition, seasonStartDate);
        LocalDate saturday = weekendStart;
        LocalDate sunday = weekendStart.plusDays(1);

        // Determine Saturday/Sunday split (3-5 on Saturday, rest on Sunday)
        int saturdayMatches = 3 + random.nextInt(3); // 3, 4, or 5
        int sundayMatches = totalMatches - saturdayMatches;

        List<LocalDateTime> kickoffs = new ArrayList<>();

        // Generate Saturday kickoffs
        for (int i = 0; i < saturdayMatches; i++) {
            LocalTime time = SATURDAY_TIMES[i % SATURDAY_TIMES.length];
            kickoffs.add(LocalDateTime.of(saturday, time));
        }

        // Generate Sunday kickoffs
        for (int i = 0; i < sundayMatches; i++) {
            LocalTime time = SUNDAY_TIMES[i % SUNDAY_TIMES.length];
            kickoffs.add(LocalDateTime.of(sunday, time));
        }

        return kickoffs;
    }

    /**
     * Finds the Saturday for a given round.
     * Rounds are typically weekly, starting from the first weekend after season start.
     */
    private LocalDate findWeekendForRound(int roundPosition, LocalDate seasonStartDate) {
        // Find first Saturday after season start
        LocalDate firstSaturday = seasonStartDate;
        while (firstSaturday.getDayOfWeek() != DayOfWeek.SATURDAY) {
            firstSaturday = firstSaturday.plusDays(1);
        }

        // Add weeks for subsequent rounds
        return firstSaturday.plusWeeks(roundPosition - 1);
    }
}
