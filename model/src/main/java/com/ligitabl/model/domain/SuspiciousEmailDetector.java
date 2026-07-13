package com.ligitabl.model.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SuspiciousEmailDetector {

    private SuspiciousEmailDetector() {}

    public static final class Weights {
        public static final int DISPOSABLE_DOMAIN = 60;
        public static final int FRAGMENTATION_HIGH = 45; // avg segment len <= 2.5
        public static final int FRAGMENTATION_MEDIUM = 25; // avg segment len <= 3.5
        public static final int DOT_DENSITY = 15;
        public static final int TRAILING_DIGIT_CLUSTER = 15;
        public static final int VOWEL_STARVATION = 20;
        public static final int DIGIT_DENSITY = 15;

        private Weights() {}
    }

    public static final int SUSPICION_THRESHOLD = 50;

    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "mailinator.com",
            "guerrillamail.com",
            "10minutemail.com",
            "tempmail.com",
            "throwawaymail.com",
            "yopmail.com",
            "trashmail.com");

    public record Result(int score, List<String> reasons) {
        public boolean isSuspicious() {
            return score >= SUSPICION_THRESHOLD;
        }
    }

    public static Result analyze(String email) {
        if (email == null || email.indexOf('@') < 0) {
            return new Result(100, List.of("Missing or malformed email"));
        }

        String lower = email.toLowerCase(Locale.ROOT);
        int at = lower.lastIndexOf('@');
        String local = lower.substring(0, at);
        String domain = lower.substring(at + 1);
        String deDotted = local.replace(".", "");

        List<String> reasons = new ArrayList<>();
        int score = 0;

        if (isDisposableDomain(domain)) {
            score += Weights.DISPOSABLE_DOMAIN;
            reasons.add("Disposable/temp-mail domain");
        }

        List<String> segments =
                Arrays.stream(local.split("\\.")).filter(s -> !s.isEmpty()).toList();
        if (segments.size() >= 4) {
            double avgLen =
                    segments.stream().mapToInt(String::length).average().orElse(0);
            if (avgLen <= 2.5) {
                score += Weights.FRAGMENTATION_HIGH;
                reasons.add("Highly fragmented local part (%d segments, avg %.1f chars)"
                        .formatted(segments.size(), avgLen));
            } else if (avgLen <= 3.5) {
                score += Weights.FRAGMENTATION_MEDIUM;
                reasons.add(
                        "Fragmented local part (%d segments, avg %.1f chars)".formatted(segments.size(), avgLen));
            }
        }

        long dotCount = local.chars().filter(c -> c == '.').count();
        if (!local.isEmpty() && (double) dotCount / local.length() > 0.2) {
            score += Weights.DOT_DENSITY;
            reasons.add("High dot density");
        }

        if (deDotted.matches(".*[a-z]\\d{2,}$")) {
            score += Weights.TRAILING_DIGIT_CLUSTER;
            reasons.add("Trailing digit cluster");
        }

        String lettersOnly = deDotted.replaceAll("[^a-z]", "");
        if (lettersOnly.length() >= 5) {
            long vowels =
                    lettersOnly.chars().filter(c -> "aeiou".indexOf(c) >= 0).count();
            if ((double) vowels / lettersOnly.length() < 0.2) {
                score += Weights.VOWEL_STARVATION;
                reasons.add("Vowel-starved / random-looking local part");
            }
        }

        if (!deDotted.isEmpty()) {
            long digitCount = deDotted.chars().filter(Character::isDigit).count();
            if ((double) digitCount / deDotted.length() > 0.3) {
                score += Weights.DIGIT_DENSITY;
                reasons.add("High digit density");
            }
        }

        return new Result(Math.min(score, 100), List.copyOf(reasons));
    }

    private static boolean isDisposableDomain(String domain) {
        return DISPOSABLE_DOMAINS.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }
}
