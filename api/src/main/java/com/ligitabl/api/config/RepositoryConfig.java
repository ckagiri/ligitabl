package com.ligitabl.api.config;

import com.ligitabl.model.dao.TeamRepoImpl;
import com.ligitabl.model.repo.TeamRepo;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RepositoryConfig {
    @Bean
    public TeamRepo teamRepo(DSLContext dsl) {
        return new TeamRepoImpl(dsl);
    }
}
