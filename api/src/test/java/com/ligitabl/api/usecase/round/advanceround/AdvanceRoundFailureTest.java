package com.ligitabl.api.usecase.round.advanceround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.usecase.shared.HierarchyValidator;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.shared.RuleViolationException;

@ExtendWith(MockitoExtension.class)
class AdvanceRoundFailureTest {

    private final UUID roundId = UUID.randomUUID();

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private RoundAdvancementService roundAdvancementService;

    @Mock
    private RoundRepo roundRepo;

    private AdvanceCurrentRoundNowUseCase advanceNow;
    private CancelRoundAdvancementUseCase cancel;

    @BeforeEach
    void setUp() {
        var defaults = new CompetitionDefaults("premier-league");
        advanceNow =
                new AdvanceCurrentRoundNowUseCase(hierarchyValidator, defaults, roundAdvancementService, roundRepo);
        cancel = new CancelRoundAdvancementUseCase(hierarchyValidator, defaults, roundAdvancementService);

        var round = Round.builder().id(roundId).build();
        var context = new HierarchyValidator.HierarchyContext(null, null, round);
        when(hierarchyValidator.resolveHierarchy("premier-league")).thenReturn(Either.right(context));
    }

    @Test
    void advanceNow_brokenRule_isAConflict() {
        when(roundAdvancementService.advanceManually(roundId)).thenThrow(new RuleViolationException("Not finalized"));

        assertThat(advanceNow.execute().getLeft()).isInstanceOf(ConflictError.class);
    }

    @Test
    void advanceNow_otherFailure_leavesAsAnExceptionSoTheTransactionRollsBack() {
        when(roundAdvancementService.advanceManually(roundId)).thenThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> advanceNow.execute()).hasMessage("save failed");
    }

    @Test
    void cancel_brokenRule_isAConflict() {
        doThrow(new RuleViolationException("Nothing scheduled"))
                .when(roundAdvancementService)
                .cancelScheduledAdvancement(roundId);

        assertThat(cancel.execute().getLeft()).isInstanceOf(ConflictError.class);
    }

    @Test
    void cancel_otherFailure_leavesAsAnExceptionSoTheTransactionRollsBack() {
        doThrow(new IllegalStateException("save failed"))
                .when(roundAdvancementService)
                .cancelScheduledAdvancement(roundId);

        assertThatThrownBy(() -> cancel.execute()).hasMessage("save failed");
    }
}
