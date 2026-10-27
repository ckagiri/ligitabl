package com.ligitabl.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.runners.importer.ImportMatchesUseCase;
import com.ligitabl.api.runners.importer.event.ImportEventPublisher;
import com.ligitabl.api.runners.importer.event.LoggingImportEventPublisher;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ImporterUseCaseConfiguration {

    /**
     * Event publisher - using simple logging by default.
     */
    @Bean
    @Primary
    public ImportEventPublisher importEventPublisher() {
        log.info("Creating LoggingImportEventPublisher");
        return new LoggingImportEventPublisher();
    }

    @Bean
    public ImportMatchesUseCase importMatchesUseCase(
            FootballDataClient footballDataClient,
            SeasonRepo seasonRepo,
            TeamRepo teamRepo,
            RoundRepo roundRepo,
            MatchRepo matchRepo,
            ImportEventPublisher eventPublisher) {

        log.info("Creating ImportMatchesUseCase");

        return new ImportMatchesUseCase(footballDataClient, seasonRepo, teamRepo, roundRepo, matchRepo, eventPublisher);
    }
}
