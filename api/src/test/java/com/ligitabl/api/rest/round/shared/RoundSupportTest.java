package com.ligitabl.api.rest.round.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;

/**
 * Covers the one place {@link RoundSupport#resolveStatus} and
 * {@link RoundSupport#resolveJoinEligibilityStatus} disagree, which is the whole reason the
 * second method exists: {@code resolveStatus} short-circuits to OPEN for a round with no matches
 * loaded, <em>before</em> {@code Round.computeStatus} could report FINALIZED. Without the
 * lifecycle-aware variant, a join would be accepted into a round that has already been scored.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoundSupportTest {

    private static final UUID ROUND_ID = UUID.randomUUID();

    @Mock
    RoundRepo roundRepo;

    @Mock
    MatchRepo matchRepo;

    @Mock
    HierarchyValidator hierarchyValidator;

    private RoundSupport roundSupport;

    private RoundSupport support() {
        if (roundSupport == null) {
            roundSupport = new RoundSupport(roundRepo, matchRepo, hierarchyValidator, new CompetitionDefaults("pl"));
        }
        return roundSupport;
    }

    private Round round(boolean finalized) {
        return Round.builder()
                .id(ROUND_ID)
                .seasonId(UUID.randomUUID())
                .name("Round 1")
                .slug("round-1")
                .position(1)
                .finalized(finalized)
                .build();
    }

    @Test
    void finalizedRoundWithNoMatches_readsOpenFromResolveStatus() {
        // Documents the trap rather than endorsing it: this is why callers asking "can someone
        // still join here?" must not use resolveStatus.
        when(matchRepo.findByRoundId(ROUND_ID)).thenReturn(List.of());

        assertThat(support().resolveStatus(round(true))).isEqualTo(RoundStatus.OPEN);
    }

    @Test
    void finalizedRoundWithNoMatches_readsFinalizedFromJoinEligibilityStatus() {
        when(matchRepo.findByRoundId(ROUND_ID)).thenReturn(List.of());

        assertThat(support().resolveJoinEligibilityStatus(round(true))).isEqualTo(RoundStatus.FINALIZED);
    }

    @Test
    void unfinalizedRoundWithNoMatches_readsOpenFromBoth() {
        // The state at the moment predictions open, before fixtures are synced — must stay
        // joinable under both.
        when(matchRepo.findByRoundId(ROUND_ID)).thenReturn(List.of());

        assertThat(support().resolveStatus(round(false))).isEqualTo(RoundStatus.OPEN);
        assertThat(support().resolveJoinEligibilityStatus(round(false))).isEqualTo(RoundStatus.OPEN);
    }

    @Test
    void nullRound_isUnknownUnderBoth() {
        assertThat(support().resolveStatus(null)).isEqualTo(RoundStatus.UNKNOWN);
        assertThat(support().resolveJoinEligibilityStatus(null)).isEqualTo(RoundStatus.UNKNOWN);
    }
}
