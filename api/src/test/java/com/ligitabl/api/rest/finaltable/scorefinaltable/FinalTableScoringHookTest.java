package com.ligitabl.api.rest.finaltable.scorefinaltable;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.Season;

@ExtendWith(MockitoExtension.class)
class FinalTableScoringHookTest {

    @Mock
    private ScoreFinalTablePredictionsUseCase scoreUseCase;

    @InjectMocks
    private FinalTableScoringHook hook;

    private final Season season = Season.builder().id(UUID.randomUUID()).build();

    @Test
    void scoresAgainstTheFinalRoundNeverCurrentStandings() {
        // CURRENT would write provisional scores into a completed season. The enum has no default,
        // so this is the one place that has to state FINAL_ROUND and be checked for it.
        when(scoreUseCase.execute(any(), any(), anyBoolean()))
                .thenReturn(new ScoreFinalTablePredictionsUseCase.ScoringSummary(3, 0, 0));

        hook.onSeasonCompleted(season);

        verify(scoreUseCase).execute(season, StandingsSource.FINAL_ROUND, false);
    }

    @Test
    void doesNotRecompute() {
        // Completion is the first scoring event, so already-scored rows are left alone; an admin
        // recompute is the deliberate override.
        when(scoreUseCase.execute(any(), any(), anyBoolean()))
                .thenReturn(new ScoreFinalTablePredictionsUseCase.ScoringSummary(0, 0, 0));

        hook.onSeasonCompleted(season);

        verify(scoreUseCase).execute(any(), eq(StandingsSource.FINAL_ROUND), eq(false));
    }

    @Test
    void swallowsAScoringFailureSoCompletionIsNeverBlocked() {
        when(scoreUseCase.execute(any(), any(), anyBoolean())).thenThrow(new IllegalStateException("scorer down"));

        assertThatCode(() -> hook.onSeasonCompleted(season)).doesNotThrowAnyException();
    }
}
