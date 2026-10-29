package com.ligitabl.api.scheduling.seasonactivation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.scheduling.health.JobHeartbeat;
import com.ligitabl.api.scheduling.health.ScheduledJobRunner;
import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.api.usecase.season.switchseason.AutoActivateSeasonResult;
import com.ligitabl.api.usecase.season.switchseason.AutoActivateSeasonUseCase;

@ExtendWith(MockitoExtension.class)
class SeasonActivationJobTest {

    @Mock
    AutoActivateSeasonUseCase autoActivateSeasonUseCase;

    private SeasonActivationJob job;

    @BeforeEach
    void setUp() {
        var heartbeat = new JobHeartbeat(TestClock.FIXED);
        job = new SeasonActivationJob(
                new CompetitionDefaults("premier-league"),
                autoActivateSeasonUseCase,
                new ScheduledJobRunner(heartbeat),
                heartbeat);
    }

    @Test
    void checksTheDefaultCompetition() {
        when(autoActivateSeasonUseCase.execute("premier-league")).thenReturn(new AutoActivateSeasonResult.NotDue());

        job.run();

        verify(autoActivateSeasonUseCase).execute("premier-league");
    }

    @Test
    void useCaseFailure_doesNotEscape() {
        when(autoActivateSeasonUseCase.execute("premier-league")).thenThrow(new IllegalStateException("db down"));

        assertThatCode(job::run).doesNotThrowAnyException();
    }
}
