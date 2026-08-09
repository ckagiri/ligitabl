package com.ligitabl.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FinalTablePredictionTest {

    private static final Instant CREATED = Instant.parse("2026-08-01T10:00:00Z");

    private static FinalTablePrediction newPrediction() {
        return FinalTablePrediction.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .seasonId(UUID.randomUUID())
                .rankings(List.of(TeamRank.of("ARS", 1), TeamRank.of("CHE", 2)))
                .settledAt(CREATED)
                .build();
    }

    private static SwapChange change(Instant at) {
        return new SwapChange(at, "ARS:1→2", "CHE:2→1");
    }

    @Test
    void newPredictionHasNoSwapsAndIsNotScored() {
        FinalTablePrediction prediction = newPrediction();

        assertThat(prediction.getSwaps()).isEmpty();
        assertThat(prediction.getSwapCount()).isZero();
        assertThat(prediction.isScored()).isFalse();
    }

    @Test
    void addSwapKeepsSwapsAndCountInStep() {
        FinalTablePrediction prediction = newPrediction();

        prediction.addSwap(change(CREATED.plusSeconds(60)), CREATED.plusSeconds(60));
        prediction.addSwap(change(CREATED.plusSeconds(120)), CREATED.plusSeconds(120));

        assertThat(prediction.getSwaps()).hasSize(2);
        assertThat(prediction.getSwapCount()).isEqualTo(2);
    }

    @Test
    void addSwapAdvancesSettledAt() {
        FinalTablePrediction prediction = newPrediction();
        Instant later = CREATED.plusSeconds(3600);

        prediction.addSwap(change(later), later);

        assertThat(prediction.getSettledAt()).isEqualTo(later);
    }

    @Test
    void settledAtIsUntouchedWhenNoSwapIsAdded() {
        // The tiebreak invariant: settledAt is only ever moved by a real swap, so a save that
        // carries none must leave a never-swapped row sitting at its create date.
        FinalTablePrediction prediction = newPrediction();

        assertThat(prediction.getSettledAt()).isEqualTo(CREATED);
        assertThat(prediction.getSwapCount()).isZero();
    }

    @Test
    void settledAtIsNeverRewound() {
        // A player who swapped in August and reopens the page months later must keep their
        // August commitment time; nothing may reset settledAt back toward the create date.
        FinalTablePrediction prediction = newPrediction();
        Instant august = CREATED.plusSeconds(3600);
        prediction.addSwap(change(august), august);

        assertThat(prediction.getSettledAt()).isEqualTo(august);
        assertThat(prediction.getSettledAt()).isAfter(CREATED);
    }

    @Test
    void isScoredFollowsScoredAt() {
        FinalTablePrediction prediction = newPrediction();
        assertThat(prediction.isScored()).isFalse();

        prediction.setScoredAt(Instant.parse("2027-05-24T18:00:00Z"));
        assertThat(prediction.isScored()).isTrue();

        // Clearing the results returns the row to the waiting state.
        prediction.setScoredAt(null);
        assertThat(prediction.isScored()).isFalse();
    }
}
