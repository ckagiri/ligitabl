package com.ligitabl.api.usecases.standings.calc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.api.runners.calcstandings.CalcStandingsRunner;
import com.ligitabl.api.runners.calcstandings.CalculateRoundStandingsCommand;
import com.ligitabl.api.runners.calcstandings.CalculateRoundStandingsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.DefaultApplicationArguments;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.config.WorkflowConfiguration;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

class CalcStandingsRunnerTest {

    @Mock
    CalculateRoundStandingsUseCase calculateRoundStandingsUseCase;

    @Mock
    HierarchyValidator hierarchyValidator;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    WorkflowConfiguration config;

    CalcStandingsRunner runner;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        config = new WorkflowConfiguration();
        config.setExitAfter(false);

        runner = new CalcStandingsRunner(
                calculateRoundStandingsUseCase, hierarchyValidator, competitionDefaults, seasonRepo, roundRepo, config);
    }

    @Test
    void validationFailure_doesNotCallReposOrUseCase() throws Exception {
        when(hierarchyValidator.validateCompetition("premier-league"))
                .thenReturn(Either.left(UseCaseErrors.validation("nope")));

        runner.run(new DefaultApplicationArguments(new String[] {}));

        verify(hierarchyValidator).validateCompetition("premier-league");
        verifyNoInteractions(seasonRepo, roundRepo, calculateRoundStandingsUseCase);
    }

    @Test
    void missingActiveSeason_doesNotQuerySeasonOrRounds() throws Exception {
        var competition = Competition.builder()
                .id(UUID.randomUUID())
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .name("Premier League")
                .build();

        when(hierarchyValidator.validateCompetition("premier-league")).thenReturn(Either.right(competition));

        runner.run(new DefaultApplicationArguments(new String[] {}));

        verify(hierarchyValidator).validateCompetition("premier-league");
        verifyNoInteractions(seasonRepo, roundRepo, calculateRoundStandingsUseCase);
    }

    @Test
    void happyPath_executesUseCaseOncePerRound() throws Exception {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .name("Premier League")
                .activeSeasonId(seasonId)
                .build();

        var season = Season.builder()
                .id(seasonId)
                .slug(SeasonSlug.of("2024-25"))
                .competitionId(competitionId)
                .maxRounds(38)
                .build();

        var rounds = List.of(
                Round.builder()
                        .id(UUID.randomUUID())
                        .seasonId(seasonId)
                        .position(1)
                        .name("R1")
                        .slug("r1")
                        .build(),
                Round.builder()
                        .id(UUID.randomUUID())
                        .seasonId(seasonId)
                        .position(2)
                        .name("R2")
                        .slug("r2")
                        .build(),
                Round.builder()
                        .id(UUID.randomUUID())
                        .seasonId(seasonId)
                        .position(3)
                        .name("R3")
                        .slug("r3")
                        .build());

        when(hierarchyValidator.validateCompetition("premier-league")).thenReturn(Either.right(competition));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findBySeasonIdOrderByPosition(seasonId)).thenReturn(rounds);

        when(calculateRoundStandingsUseCase.execute(any(CalculateRoundStandingsCommand.class)))
                .thenReturn(Either.right(List.of()));

        runner.run(new DefaultApplicationArguments(new String[] {}));

        verify(calculateRoundStandingsUseCase, times(3)).execute(any(CalculateRoundStandingsCommand.class));
        verify(roundRepo).findBySeasonIdOrderByPosition(seasonId);
    }
}
