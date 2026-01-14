package com.ligitabl.api.usecases.match.transitionmatchstatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.api.usecases.matchadmin.transitionmatchstatus.TransitionMatchCommand;
import com.ligitabl.api.usecases.matchadmin.transitionmatchstatus.TransitionMatchStatusUseCase;
import com.ligitabl.api.usecases.matchadmin.transitionmatchstatus.TransitionResult;
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
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class TransitionMatchStatusUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private Clock clock;

    private TransitionMatchStatusUseCase useCase;

    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;

    private Competition competition;
    private Season season;
    private Round round;

    @BeforeEach
    void setUp() {
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(seasonId)
                .build();

        season = Season.builder()
                .id(seasonId)
                .clientId(1)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(38)
                .currentRoundId(roundId)
                .currentMatchDay(1)
                .mainContestId(UUID.randomUUID())
                .build();

        round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Matchday 1")
                .slug("md-1")
                .position(1)
                .finalized(false)
                .build();

        when(hierarchyValidator.resolveHierarchy(anyString(), any()))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(season, round)));

                useCase = new TransitionMatchStatusUseCase(matchRepo, hierarchyValidator, competitionDefaults, clock);
    }

    @Test
    void transition_successfulTransition_returnsRight() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.SCHEDULED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.POSTPONED)
                .reason("Heavy rain")
                .build();

        Instant now = Instant.parse("2026-01-13T10:00:00Z");

        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));
        when(clock.instant()).thenReturn(now);

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isRight());
        TransitionResult ok = result.get();
        assertEquals(MatchStatus.SCHEDULED, ok.getOldStatus());
        assertEquals(MatchStatus.POSTPONED, ok.getNewStatus());
        assertEquals(round.getPosition(), ok.getRoundPosition());
        assertEquals(now, ok.getTimestamp());

        verify(matchRepo).save(argThat(m -> m.getStatus() == MatchStatus.POSTPONED && m.isWasPostponed()));
    }

    @Test
    void transitionToFinished_withoutScore_returnsLeft() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.LIVE)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.FINISHED)
                .reason("Full time")
                .build();

        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isLeft());
        verify(matchRepo, never()).save(any());
    }

    @Test
    void transition_invalidTransition_returnsLeft() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.FINISHED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.POSTPONED)
                .reason("Invalid")
                .build();

        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isLeft());
        verify(matchRepo, never()).save(any());
    }

    @Test
    void transition_matchNotFound_returnsLeft() {
        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug("missing")
                .newStatus(MatchStatus.LIVE)
                .reason("Start")
                .build();

        when(matchRepo.findByRoundIdAndSlug(roundId, "missing")).thenReturn(Optional.empty());

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isLeft());
        verify(matchRepo, never()).save(any());
    }
}
