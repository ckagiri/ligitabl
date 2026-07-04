package com.ligitabl.api.rest.season.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class CompleteSeasonUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    MatchRepo matchRepo;

    private CompleteSeasonUseCase useCase;
    private UUID seasonId;
    private UUID roundId;

    @BeforeEach
    void setUp() {
        useCase = new CompleteSeasonUseCase(seasonRepo, roundRepo, matchRepo, competitionDefaults);
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
    }

    @Test
    void noActiveSeason_returnsSeasonNotFound() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());

        var result = useCase.execute();

        assertThat(result).isInstanceOf(CompleteSeasonUseCase.Result.SeasonNotFound.class);
    }

    @Test
    void currentRoundNotFound_returnsSeasonNotEligible() {
        Season season = buildSeason(3);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.empty());

        var result = useCase.execute();

        assertThat(result).isInstanceOf(CompleteSeasonUseCase.Result.SeasonNotEligible.class);
        verify(seasonRepo, never()).save(any());
    }

    @Test
    void currentRoundNotLastRound_returnsSeasonNotEligible() {
        Season season = buildSeason(3);
        Round round = buildRound(2, true, true);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        var result = useCase.execute();

        assertThat(result).isInstanceOf(CompleteSeasonUseCase.Result.SeasonNotEligible.class);
        verify(seasonRepo, never()).save(any());
    }

    @Test
    void lastRoundNotFinalized_returnsSeasonNotEligible() {
        Season season = buildSeason(3);
        Round round = buildRound(3, false, false);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        var result = useCase.execute();

        assertThat(result).isInstanceOf(CompleteSeasonUseCase.Result.SeasonNotEligible.class);
        verify(seasonRepo, never()).save(any());
    }

    @Test
    void lastRoundFinalizedButNotAdvanced_returnsSeasonNotEligible() {
        Season season = buildSeason(3);
        Round round = buildRound(3, true, false);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        var result = useCase.execute();

        assertThat(result).isInstanceOf(CompleteSeasonUseCase.Result.SeasonNotEligible.class);
        verify(seasonRepo, never()).save(any());
    }

    @Test
    void lastRoundFinalizedAndAdvanced_completesSeason() {
        Season season = buildSeason(3);
        Round round = buildRound(3, true, true);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.findFirstNotFinalizedOrAdvanced(seasonId)).thenReturn(Optional.empty());
        when(matchRepo.allMatchesFinished(roundId)).thenReturn(true);

        var result = useCase.execute();

        assertThat(result).isInstanceOf(CompleteSeasonUseCase.Result.Ok.class);
        assertThat(season.isCompleted()).isTrue();
        assertThat(season.getCompletedAt()).isNotNull();
        verify(seasonRepo).save(season);
    }

    @Test
    void earlierRoundNotFinalized_returnsSeasonNotEligible() {
        Season season = buildSeason(3);
        Round round = buildRound(3, true, true);
        Round notReadyRound = buildOtherRound(2, false, false);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.findFirstNotFinalizedOrAdvanced(seasonId)).thenReturn(Optional.of(notReadyRound));

        var result = useCase.execute();

        assertThat(result).isInstanceOf(CompleteSeasonUseCase.Result.SeasonNotEligible.class);
        verify(seasonRepo, never()).save(any());
    }

    @Test
    void lastRoundMatchNotFinished_returnsSeasonNotEligible() {
        Season season = buildSeason(3);
        Round round = buildRound(3, true, true);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.allMatchesFinished(roundId)).thenReturn(false);

        var result = useCase.execute();

        assertThat(result).isInstanceOf(CompleteSeasonUseCase.Result.SeasonNotEligible.class);
        verify(seasonRepo, never()).save(any());
    }

    @Test
    void wouldBecomeInactive_rejectsWithoutSaving() {
        // endDate still in the future: once completed=true, pastActualEnd stays false and (with
        // preSeasonOpensAt/predictionsOpenAt both null) the season lands on INACTIVE, not
        // OFF_SEASON — the "outgoing season needs its pre-season dates configured" case.
        Season season = Season.builder()
                .id(seasonId)
                .clientId(1)
                .competitionId(UUID.randomUUID())
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(LocalDate.now().minusMonths(9))
                .endDate(LocalDate.now().plusMonths(1))
                .maxRounds(3)
                .completed(false)
                .currentRoundId(roundId)
                .build();
        Round round = buildRound(3, true, true);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.allMatchesFinished(roundId)).thenReturn(true);
        when(roundRepo.findFirstNotFinalizedOrAdvanced(seasonId)).thenReturn(Optional.empty());

        var result = useCase.execute();

        assertThat(result).isInstanceOf(CompleteSeasonUseCase.Result.SeasonNotEligible.class);
        verify(seasonRepo, never()).save(any());
    }

    private Season buildSeason(int maxRounds) {
        // endDate in the past: once completed=true, this makes pastActualEnd true and
        // preSeasonOpensAt/predictionsOpenAt null lands the season on OFF_SEASON, not INACTIVE —
        // the realistic "season just ended, pre-season dates not configured yet" case.
        return Season.builder()
                .id(seasonId)
                .clientId(1)
                .competitionId(UUID.randomUUID())
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(LocalDate.now().minusMonths(9))
                .endDate(LocalDate.now().minusDays(1))
                .maxRounds(maxRounds)
                .completed(false)
                .currentRoundId(roundId)
                .build();
    }

    private Round buildRound(int position, boolean finalized, boolean advanced) {
        return Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(position)
                .finalized(finalized)
                .advanced(advanced)
                .name("Round " + position)
                .slug("round-" + position)
                .build();
    }

    /** A round other than the season's current round (distinct id), for the all-rounds check. */
    private Round buildOtherRound(int position, boolean finalized, boolean advanced) {
        return Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .position(position)
                .finalized(finalized)
                .advanced(advanced)
                .name("Round " + position)
                .slug("round-" + position)
                .build();
    }
}
