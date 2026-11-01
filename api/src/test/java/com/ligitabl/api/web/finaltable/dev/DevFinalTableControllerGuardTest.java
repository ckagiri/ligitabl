package com.ligitabl.api.web.finaltable.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.usecase.finaltable.scorefinaltable.ScoreFinalTablePredictionsUseCase;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

/**
 * The dev trigger overwrites real scores with provisional ones, so it is guarded by a flag and a
 * profile. The profile is the guard that must hold: a flag is one application.yml mistake from
 * production.
 */
class DevFinalTableControllerGuardTest {

    private static final String FLAG = "ligitabl.final-table.dev-preview.enabled";

    @Configuration
    static class Collaborators {
        @Bean
        SeasonRepo seasonRepo() {
            return mock(SeasonRepo.class);
        }

        @Bean
        FinalTablePredictionRepo finalTablePredictionRepo() {
            return mock(FinalTablePredictionRepo.class);
        }

        @Bean
        ScoreFinalTablePredictionsUseCase scoreFinalTablePredictionsUseCase() {
            return mock(ScoreFinalTablePredictionsUseCase.class);
        }

        @Bean
        CompetitionDefaults competitionDefaults() {
            return new CompetitionDefaults("premier-league");
        }
    }

    /**
     * Profiles go through {@code spring.profiles.active}, not a MockEnvironment: replacing the
     * environment discards what {@code withPropertyValues} writes, so the "absent" cases would pass
     * for the wrong reason.
     */
    private ApplicationContextRunner runnerWithProfiles(String... profiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Collaborators.class, DevFinalTableController.class)
                .withPropertyValues("spring.profiles.active=" + String.join(",", profiles));
    }

    @Test
    void isRegisteredInNonProdWhenTheFlagIsOn() {
        runnerWithProfiles("dev").withPropertyValues(FLAG + "=true").run(context -> assertThat(context)
                .hasSingleBean(DevFinalTableController.class));
    }

    @Test
    void isAbsentUnderTheProdProfileEvenWithTheFlagOn() {
        // The profile guard: unreachable in prod, not merely disabled.
        runnerWithProfiles("prod").withPropertyValues(FLAG + "=true").run(context -> assertThat(context)
                .doesNotHaveBean(DevFinalTableController.class));
    }

    @Test
    void isAbsentInNonProdWhenTheFlagIsOff() {
        runnerWithProfiles("dev").withPropertyValues(FLAG + "=false").run(context -> assertThat(context)
                .doesNotHaveBean(DevFinalTableController.class));
    }

    @Test
    void isAbsentWhenTheFlagIsUnset() {
        // Defaults to inert: a non-prod environment that never configures the flag stays safe.
        runnerWithProfiles("dev").run(context -> assertThat(context).doesNotHaveBean(DevFinalTableController.class));
    }
}
