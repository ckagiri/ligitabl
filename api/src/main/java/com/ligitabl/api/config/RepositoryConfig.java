package com.ligitabl.api.config;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import com.ligitabl.model.domain.service.ScoringEngine;
import com.ligitabl.model.domain.service.StandingsCalculator;

import com.ligitabl.model.infra.CompetitionPersistenceAdapter;
import com.ligitabl.model.infra.ContestPersistenceAdapter;
import com.ligitabl.model.infra.EntryPersistenceAdapter;
import com.ligitabl.model.infra.MatchPersistenceAdapter;
import com.ligitabl.model.infra.RoundPersistenceAdapter;
import com.ligitabl.model.infra.RoundResultPersistenceAdapter;
import com.ligitabl.model.infra.RoundSubmissionPersistenceAdapter;
import com.ligitabl.model.infra.SeasonPersistenceAdapter;
import com.ligitabl.model.infra.SeasonPredictionPersistenceAdapter;
import com.ligitabl.model.infra.StandingsPersistenceAdapter;
import com.ligitabl.model.infra.TeamPersistenceAdapter;
import com.ligitabl.model.infra.UserPersistenceAdapter;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

@Configuration
public class RepositoryConfig {
    @Bean
    public CompetitionDefaults competitionDefaults(
            @Value("${ligitabl.default-competition-slug:premier-league}") String defaultCompetitionSlug
    ) {
        return new CompetitionDefaults(defaultCompetitionSlug);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public StandingsCalculator standingsCalculator() {
        return new StandingsCalculator();
    }

    @Bean
    public ScoringEngine scoringEngine() {
        return new ScoringEngine();
    }

    @Bean
    public TeamRepo teamRepo(DSLContext dsl) {
        return new TeamPersistenceAdapter(dsl);
    }

    @Bean
    public CompetitionRepo competitionRepo(DSLContext dsl) {
        return new CompetitionPersistenceAdapter(dsl);
    }

    @Bean
    public SeasonRepo seasonRepo(DSLContext dsl) {
        return new SeasonPersistenceAdapter(dsl);
    }

    @Bean
    public RoundRepo roundRepo(DSLContext dsl) {
        return new RoundPersistenceAdapter(dsl);
    }

    @Bean
    public MatchRepo matchRepo(DSLContext dsl) {
        return new MatchPersistenceAdapter(dsl);
    }

    @Bean
    public SeasonPredictionRepo seasonPredictionRepo(DSLContext dsl) {
        return new SeasonPredictionPersistenceAdapter(dsl);
    }

    @Bean
    public StandingsRepo standingsRepo(DSLContext dsl) {
        return new StandingsPersistenceAdapter(dsl);
    }

    @Bean
    public RoundSubmissionRepo roundSubmissionRepo(DSLContext dsl) {
        return new RoundSubmissionPersistenceAdapter(dsl);
    }

    @Bean
    public RoundResultRepo roundResultRepo(DSLContext dsl) {
        return new RoundResultPersistenceAdapter(dsl);
    }

    @Bean
    public ContestRepo contestRepo(DSLContext dsl) {
        return new ContestPersistenceAdapter(dsl);
    }

    @Bean
    public EntryRepo entryRepo(DSLContext dsl) {
        return new EntryPersistenceAdapter(dsl);
    }

    @Bean
    public UserRepo userRepo(DSLContext dsl) {
        return new UserPersistenceAdapter(dsl);
    }
}
