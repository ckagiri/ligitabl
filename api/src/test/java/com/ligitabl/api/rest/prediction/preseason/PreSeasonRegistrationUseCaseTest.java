package com.ligitabl.api.rest.prediction.preseason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class PreSeasonRegistrationUseCaseTest {

    private static final CompetitionDefaults DEFAULTS = new CompetitionDefaults("premier-league");

    @Mock SeasonRepo seasonRepo;
    @Mock SeasonPredictionRepo predictionRepo;
    @Mock ContestRepo contestRepo;
    @Mock EntryRepo entryRepo;

    private PreSeasonRegistrationUseCase useCase;

    private UUID userId;
    private Season preSeasonSeason;
    private Contest mainContest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        useCase = new PreSeasonRegistrationUseCase(DEFAULTS, seasonRepo, predictionRepo, contestRepo, entryRepo);

        UUID contestId = UUID.randomUUID();
        mainContest = Contest.builder()
                .id(contestId)
                .seasonId(UUID.randomUUID())
                .name("Premier League 26/27")
                .fromRoundPosition(1)
                .build();

        preSeasonSeason = Season.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .competitionId(UUID.randomUUID())
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(LocalDate.now().plusMonths(1))
                .endDate(LocalDate.now().plusMonths(10))
                .maxRounds(38)
                .completed(false)
                .predictionsOpenAt(OffsetDateTime.now().plusDays(7))
                .mainContestId(contestId)
                .initialRankings(List.of(
                        new TeamRank("ARS", 1),
                        new TeamRank("CHE", 2),
                        new TeamRank("LIV", 3),
                        new TeamRank("MCI", 4),
                        new TeamRank("MUN", 5)))
                .build();
    }

    @Test
    void seasonNotInPreSeason_returnsNotPreSeason() {
        Season completedSeason = Season.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .competitionId(UUID.randomUUID())
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .startDate(LocalDate.now().minusMonths(10))
                .endDate(LocalDate.now().minusDays(1))
                .completed(true)
                .initialRankings(List.of())
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(completedSeason));

        Either<PreSeasonRegistrationError, PreSeasonRegistrationResult> result =
                useCase.execute(userId, new PreSeasonRegistrationCommand(List.of()));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(PreSeasonRegistrationError.NotPreSeason.class);
    }

    @Test
    void alreadyRegistered_returnsAlreadyJoined() {
        UUID existingPredictionId = UUID.randomUUID();
        SeasonPrediction existing = SeasonPrediction.builder()
                .id(existingPredictionId)
                .userId(userId)
                .seasonId(preSeasonSeason.getId())
                .initialRankings(List.of())
                .currentRankings(List.of())
                .atRoundNumber(0)
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(preSeasonSeason));
        when(predictionRepo.findByUserAndSeason(userId, preSeasonSeason.getId())).thenReturn(Optional.of(existing));

        Either<PreSeasonRegistrationError, PreSeasonRegistrationResult> result =
                useCase.execute(userId, new PreSeasonRegistrationCommand(List.of()));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(PreSeasonRegistrationError.AlreadyJoined.class);
        assertThat(((PreSeasonRegistrationError.AlreadyJoined) result.getLeft()).existingPredictionId())
                .isEqualTo(existingPredictionId);
    }

    @Test
    void zeroSwaps_createsSeasonPredictionWithUnchangedRankings() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(preSeasonSeason));
        when(predictionRepo.findByUserAndSeason(userId, preSeasonSeason.getId())).thenReturn(Optional.empty());
        when(contestRepo.findById(preSeasonSeason.getMainContestId())).thenReturn(Optional.of(mainContest));
        when(predictionRepo.save(any())).thenAnswer(returnsFirstArg());
        when(entryRepo.save(any())).thenAnswer(i -> {
            Entry e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        Either<PreSeasonRegistrationError, PreSeasonRegistrationResult> result =
                useCase.execute(userId, new PreSeasonRegistrationCommand(List.of()));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().swapsApplied()).isZero();

        verify(predictionRepo).save(argThat(p ->
                p.getAtRoundNumber() == 0
                && p.getCurrentRankings().equals(preSeasonSeason.getInitialRankings())));
    }

    @Test
    void threeValidSwaps_createsSeasonPredictionWithSwapsApplied() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(preSeasonSeason));
        when(predictionRepo.findByUserAndSeason(userId, preSeasonSeason.getId())).thenReturn(Optional.empty());
        when(contestRepo.findById(preSeasonSeason.getMainContestId())).thenReturn(Optional.of(mainContest));
        when(predictionRepo.save(any())).thenAnswer(returnsFirstArg());
        when(entryRepo.save(any())).thenAnswer(i -> {
            Entry e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        List<PreSeasonRegistrationCommand.SwapPair> swaps = List.of(
                new PreSeasonRegistrationCommand.SwapPair("ARS", "CHE"),
                new PreSeasonRegistrationCommand.SwapPair("LIV", "MCI"),
                new PreSeasonRegistrationCommand.SwapPair("MUN", "ARS"));

        Either<PreSeasonRegistrationError, PreSeasonRegistrationResult> result =
                useCase.execute(userId, new PreSeasonRegistrationCommand(swaps));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().swapsApplied()).isEqualTo(3);

        verify(predictionRepo).save(argThat(p ->
                p.getAtRoundNumber() == 0
                && !p.getCurrentRankings().equals(preSeasonSeason.getInitialRankings())
                && p.getSwaps().get(0).getRound() == 0));
    }

    @Test
    void sixSwaps_returnsTooManySwaps() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(preSeasonSeason));
        when(predictionRepo.findByUserAndSeason(any(), any())).thenReturn(Optional.empty());

        List<PreSeasonRegistrationCommand.SwapPair> swaps = List.of(
                new PreSeasonRegistrationCommand.SwapPair("ARS", "CHE"),
                new PreSeasonRegistrationCommand.SwapPair("LIV", "MCI"),
                new PreSeasonRegistrationCommand.SwapPair("MUN", "ARS"),
                new PreSeasonRegistrationCommand.SwapPair("CHE", "LIV"),
                new PreSeasonRegistrationCommand.SwapPair("MCI", "MUN"),
                new PreSeasonRegistrationCommand.SwapPair("ARS", "LIV"));

        Either<PreSeasonRegistrationError, PreSeasonRegistrationResult> result =
                useCase.execute(userId, new PreSeasonRegistrationCommand(swaps));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(PreSeasonRegistrationError.TooManySwaps.class);
    }

    @Test
    void invalidTeamCode_returnsInvalidTeamCode() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(preSeasonSeason));
        when(predictionRepo.findByUserAndSeason(any(), any())).thenReturn(Optional.empty());

        Either<PreSeasonRegistrationError, PreSeasonRegistrationResult> result =
                useCase.execute(userId, new PreSeasonRegistrationCommand(
                        List.of(new PreSeasonRegistrationCommand.SwapPair("XXX", "ARS"))));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(PreSeasonRegistrationError.InvalidTeamCode.class);
        assertThat(((PreSeasonRegistrationError.InvalidTeamCode) result.getLeft()).code()).isEqualTo("XXX");
    }

    @Test
    void sameTeamBothSides_returnsSameTeam() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(preSeasonSeason));
        when(predictionRepo.findByUserAndSeason(any(), any())).thenReturn(Optional.empty());

        Either<PreSeasonRegistrationError, PreSeasonRegistrationResult> result =
                useCase.execute(userId, new PreSeasonRegistrationCommand(
                        List.of(new PreSeasonRegistrationCommand.SwapPair("ARS", "ARS"))));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(PreSeasonRegistrationError.SameTeam.class);
    }
}
