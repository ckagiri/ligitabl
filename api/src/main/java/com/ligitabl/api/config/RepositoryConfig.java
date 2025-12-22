package com.ligitabl.api.config;

import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ligitabl.model.infra.CompetitionPersistenceAdapter;
import com.ligitabl.model.infra.MatchPersistenceAdapter;
import com.ligitabl.model.infra.RoundPersistenceAdapter;
import com.ligitabl.model.infra.SeasonPersistenceAdapter;
import com.ligitabl.model.infra.TeamPersistenceAdapter;
import com.ligitabl.model.infra.UserPersistenceAdapter;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

@Configuration
public class RepositoryConfig {
    @Bean
    public TeamRepo teamDao(DSLContext dsl) {
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
    public UserRepo userRepo(DSLContext dsl) {
        return new UserPersistenceAdapter(dsl);
    }
}
