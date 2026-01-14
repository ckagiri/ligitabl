package com.ligitabl.api.usecases.match.reschedulematch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.api.usecases.matchadmin.reschedulematch.RescheduleMatchCommand;
import com.ligitabl.api.usecases.matchadmin.reschedulematch.RescheduleMatchUseCase;
import com.ligitabl.api.usecases.matchadmin.reschedulematch.RescheduleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.MatchRepo;

@ExtendWith(MockitoExtension.class)
class RescheduleMatchUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private Clock clock;

    private RescheduleMatchUseCase useCase;

    private UUID competitionId;
    private UUID seasonId;
    private UUID currentRoundId;
    private UUID targetRoundId;

    private Competition competition;
    private Season seasonLive;
    private Season seasonSetup;
    private Round currentRound;
    private Round targetRound;

    @BeforeEach
    void setUp() {
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        currentRoundId = UUID.randomUUID();
        targetRoundId = UUID.randomUUID();

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(seasonId)
                .build();

        seasonLive = Season.builder()
                .id(seasonId)
                .clientId(1)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(38)
                .currentRoundId(currentRoundId)
                .currentMatchDay(10)
                .mainContestId(UUID.randomUUID())
                .build();

        seasonSetup = Season.builder()
                .id(seasonId)
                .clientId(1)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(38)
                .currentRoundId(currentRoundId)
                .currentMatchDay(10)
                .mainContestId(null)
                .detachedContestId(UUID.randomUUID())
                .build();

        currentRound = Round.builder()
                .id(currentRoundId)
                .seasonId(seasonId)
                .name("Matchday 10")
                .slug("md-10")
                .position(10)
                .finalized(false)
                .build();

        targetRound = Round.builder()
                .id(targetRoundId)
                .seasonId(seasonId)
                .name("Matchday 20")
                .slug("md-20")
                .position(20)
                .finalized(false)
                .build();

        useCase = new RescheduleMatchUseCase(matchRepo, hierarchyValidator, competitionDefaults, clock);
    }

    @Test
    void reschedule_postponedMatch_succeeds_and_setsScheduled_inLiveMode() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(currentRoundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.POSTPONED)
                .wasPostponed(true)
                .build();

        RescheduleMatchCommand cmd = RescheduleMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newRoundPosition(20)
                .reason("FA confirmed new date")
                .build();

        Instant now = Instant.parse("2026-01-13T10:00:00Z");

        when(hierarchyValidator.resolveHierarchy(anyString(), any()))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(seasonLive, currentRound)));
        when(matchRepo.findByRoundIdAndSlug(currentRoundId, match.getSlug())).thenReturn(Optional.of(match));
        when(hierarchyValidator.validateRound(seasonId, 20)).thenReturn(Either.right(targetRound));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));
        when(clock.instant()).thenReturn(now);

        Either<UseCaseError, RescheduleResult> result = useCase.execute(cmd);

        assertTrue(result.isRight());
        RescheduleResult ok = result.get();
        assertEquals(MatchStatus.POSTPONED, ok.getOldStatus());
        assertEquals(MatchStatus.SCHEDULED, ok.getNewStatus());
        assertEquals(10, ok.getFromRound());
        assertEquals(20, ok.getToRound());
        assertEquals(now, ok.getTimestamp());

        verify(matchRepo).save(argThat(m -> m.getRoundId().equals(targetRoundId) && m.getStatus() == MatchStatus.SCHEDULED));
    }

    @Test
    void reschedule_inSetupMode_skipsLiveModeValidation_and_preservesStatus() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(currentRoundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.LIVE)
                .build();

        RescheduleMatchCommand cmd = RescheduleMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newRoundPosition(20)
                .reason("Setup fixtures")
                .build();

        when(hierarchyValidator.resolveHierarchy(anyString(), any()))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(seasonSetup, currentRound)));
        when(matchRepo.findByRoundIdAndSlug(currentRoundId, match.getSlug())).thenReturn(Optional.of(match));
        when(hierarchyValidator.validateRound(seasonId, 20)).thenReturn(Either.right(targetRound));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));

        Either<UseCaseError, RescheduleResult> result = useCase.execute(cmd);

        assertTrue(result.isRight());
        verify(matchRepo).save(argThat(m -> m.getRoundId().equals(targetRoundId) && m.getStatus() == MatchStatus.LIVE));
    }

    @Test
    void reschedule_suspendedMatch_returnsLeftInvalidState_inLiveMode() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(currentRoundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.SUSPENDED)
                .wasSuspended(true)
                .build();

        RescheduleMatchCommand cmd = RescheduleMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newRoundPosition(20)
                .reason("Invalid")
                .build();

        when(hierarchyValidator.resolveHierarchy(anyString(), any()))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(seasonLive, currentRound)));
        when(matchRepo.findByRoundIdAndSlug(currentRoundId, match.getSlug())).thenReturn(Optional.of(match));
        when(hierarchyValidator.validateRound(seasonId, 20)).thenReturn(Either.right(targetRound));

        Either<UseCaseError, RescheduleResult> result = useCase.execute(cmd);

        assertTrue(result.isLeft());
        verify(matchRepo, never()).save(any());
    }

    @Test
    void reschedule_roundNotFound_returnsLeft() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(currentRoundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.POSTPONED)
                .wasPostponed(true)
                .build();

        RescheduleMatchCommand cmd = RescheduleMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newRoundPosition(99)
                .reason("Non-existent")
                .build();

        when(hierarchyValidator.resolveHierarchy(anyString(), any()))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(seasonLive, currentRound)));
        when(matchRepo.findByRoundIdAndSlug(currentRoundId, match.getSlug())).thenReturn(Optional.of(match));
        when(hierarchyValidator.validateRound(seasonId, 99)).thenReturn(Either.left(com.ligitabl.api.shared.errors.UseCaseErrors.notFound("Round", "position", 99)));

        Either<UseCaseError, RescheduleResult> result = useCase.execute(cmd);

        assertTrue(result.isLeft());
        verify(matchRepo, never()).save(any());
    }
}
