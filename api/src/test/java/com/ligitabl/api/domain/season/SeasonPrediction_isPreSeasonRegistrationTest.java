package com.ligitabl.api.domain.season;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;

class SeasonPrediction_isPreSeasonRegistrationTest {

    @Test
    void atRoundNumberZero_returnsTrue() {
        assertThat(prediction(0).isPreSeasonRegistration()).isTrue();
    }

    @Test
    void atRoundNumberNonZero_returnsFalse() {
        assertThat(prediction(1).isPreSeasonRegistration()).isFalse();
        assertThat(prediction(5).isPreSeasonRegistration()).isFalse();
    }

    private SeasonPrediction prediction(int atRoundNumber) {
        return SeasonPrediction.builder()
                .userId(UUID.randomUUID())
                .seasonId(UUID.randomUUID())
                .currentRankings(List.of(TeamRank.of("ARS", 1)))
                .atRoundNumber(atRoundNumber)
                .build();
    }
}
