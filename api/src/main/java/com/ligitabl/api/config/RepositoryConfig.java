package com.ligitabl.api.config;

import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ligitabl.model.infra.TeamPersistenceAdapter;
import com.ligitabl.model.repo.TeamRepo;

@Configuration
public class RepositoryConfig {
    @Bean
    public TeamRepo teamDao(DSLContext dsl) {
        return new TeamPersistenceAdapter(dsl);
    }
}
