package com.ligitabl.api.rest.prediction.whatif;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.WhatIfPrediction;
import com.ligitabl.model.repo.WhatIfPredictionRepo;

@ExtendWith(MockitoExtension.class)
class SaveWhatIfPredictionUseCaseTest {

    @Mock
    private WhatIfPredictionRepo whatIfPredictionRepo;

    private SaveWhatIfPredictionUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID roundId = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        useCase = new SaveWhatIfPredictionUseCase(whatIfPredictionRepo);
    }

    @Test
    void execute_shouldSaveScoresKeyedByUserAndRound() {
        UUID matchId = UUID.randomUUID();

        useCase.execute(userId, roundId, List.of(new WhatIfScore(matchId, 2, 1)));

        ArgumentCaptor<WhatIfPrediction> captor = ArgumentCaptor.forClass(WhatIfPrediction.class);
        verify(whatIfPredictionRepo).save(captor.capture());

        WhatIfPrediction saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(roundId, saved.getRoundId());
        assertEquals(1, saved.getScores().size());
        assertEquals(matchId, saved.getScores().get(0).matchId());
        assertEquals(2, saved.getScores().get(0).homeGoals());
        assertEquals(1, saved.getScores().get(0).awayGoals());
    }

    @Test
    void execute_shouldTreatNullScoresAsEmpty() {
        useCase.execute(userId, roundId, null);

        ArgumentCaptor<WhatIfPrediction> captor = ArgumentCaptor.forClass(WhatIfPrediction.class);
        verify(whatIfPredictionRepo).save(captor.capture());
        assertTrue(captor.getValue().getScores().isEmpty());
    }
}
