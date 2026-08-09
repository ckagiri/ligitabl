package com.ligitabl.api.web.finaltable.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.finaltable.scorefinaltable.ScoreFinalTablePredictionsUseCase;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

/**
 * The dev trigger overwrites real scores with provisional ones, so it is guarded twice. This pins
 * both guards — and specifically that the profile is the load-bearing one, since a flag alone is one
 * application.yml mistake away from being reachable in production.
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
     * Profiles are set through {@code spring.profiles.active} rather than by swapping in a
     * MockEnvironment: replacing the environment discards the property sources
     * {@code withPropertyValues} writes into, so the flag would never reach the condition and the
     * "absent" cases would pass for the wrong reason.
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
        // The load-bearing guard: unreachable rather than merely disabled.
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
