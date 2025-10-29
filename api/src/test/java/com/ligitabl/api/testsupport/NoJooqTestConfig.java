package com.ligitabl.api.testsupport;

import org.jooq.DSLContext;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.ligitabl.model.repo.TeamRepo;

@TestConfiguration
public class NoJooqTestConfig {

    @Bean
    public TeamRepo teamRepo() {
        return Mockito.mock(TeamRepo.class);
    }

    // Provide a dummy DSLContext bean if anything else autowires it in tests
    @Bean
    public DSLContext dslContext() {
        return Mockito.mock(DSLContext.class);
    }
}
