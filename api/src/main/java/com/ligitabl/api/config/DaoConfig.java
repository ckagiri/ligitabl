package com.ligitabl.api.config;

import com.ligitabl.model.repo.impl.TeamRepoImpl;
import com.ligitabl.model.repo.TeamRepo;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DaoConfig {
    @Bean
    public TeamRepo teamDao(DSLContext dsl) {
        return new TeamRepoImpl(dsl);
    }
}
