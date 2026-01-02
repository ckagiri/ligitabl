package com.ligitabl.api.config;

import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.importer.footballdata.FootballDataClientAdapter;
import com.ligitabl.api.importer.footballdata.FootballDataGateway;
import com.ligitabl.api.usecases.importer.ImportMatchesUseCase;
import com.ligitabl.api.importer.event.ImportEventPublisher;
import com.ligitabl.api.importer.event.LoggingImportEventPublisher;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class ImportUseCaseConfiguration {

    /**
     * Adapter that wraps your FootballDataClient.
     * Your client already returns your Either type, so no conversion needed!
     */
    @Bean
    public FootballDataGateway footballDataGateway(FootballDataClient client) {
        log.info("Creating FootballDataClientAdapter");
        return new FootballDataClientAdapter(client);
    }

    /**
     * Event publisher - using simple logging by default.
     */
    @Bean
    @Primary
    public ImportEventPublisher importEventPublisher() {
        log.info("Creating LoggingImportEventPublisher");
        return new LoggingImportEventPublisher();
    }

    /**
     * The main use case - depends only on ports (interfaces).
     * This is the core business logic.
     */
    @Bean
    public ImportMatchesUseCase importMatchesUseCase(
            FootballDataGateway footballDataGateway,
            SeasonRepo seasonRepo,
            TeamRepo teamRepo,
            RoundRepo roundRepo,
            MatchRepo matchRepo,
            ImportEventPublisher eventPublisher) {

        log.info("Creating ImportMatchesUseCase");

        return new ImportMatchesUseCase(
                footballDataGateway,
                seasonRepo,
                teamRepo,
                roundRepo,
                matchRepo,
                eventPublisher
        );
    }

    /**
     * NOTE: You need to create repository adapters that implement the port interfaces.
     * These adapters wrap your existing JPA repositories.
     *
     * See SeasonRepoAdapter.java example for how to do this.
     */
}
