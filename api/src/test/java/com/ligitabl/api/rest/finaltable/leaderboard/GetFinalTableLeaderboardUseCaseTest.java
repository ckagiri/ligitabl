package com.ligitabl.api.rest.finaltable.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.finaltable.shared.FinalTableSupport;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.model.domain.FinalTableLeaderboardEntry;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class GetFinalTableLeaderboardUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private FinalTablePredictionRepo predictionRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private HierarchyValidator hierarchyValidator;

    private static final Instant BASE = TestCalendar.MID_SEASON;

    private GetFinalTableLeaderboardUseCase useCase;
    private UUID seasonId;

    @BeforeEach
    void setUp() {
        seasonId = UUID.randomUUID();
        Season season = Season.builder().id(seasonId).completed(false).build();

        RoundSupport roundSupport = new RoundSupport(roundRepo, matchRepo, hierarchyValidator, competitionDefaults);
        useCase = new GetFinalTableLeaderboardUseCase(
                predictionRepo, new FinalTableSupport(competitionDefaults, seasonRepo, roundRepo, roundSupport));

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
    }

    private static FinalTableLeaderboardEntry entry(int position, String publicId, int score, int zeroes, long offset) {
        return new FinalTableLeaderboardEntry(
                position,
                publicId,
                "Player " + publicId,
                score,
                score - zeroes * 10,
                zeroes,
                zeroes * 10,
                3,
                BASE.plusSeconds(offset));
    }

    @Test
    void isNotRevealedWhileNothingIsScored() {
        // The waiting state: nothing to show yet, but the entry count still is.
        when(predictionRepo.countScoredBySeason(seasonId)).thenReturn(0);
        when(predictionRepo.countBySeason(seasonId)).thenReturn(42);

        var data = useCase.execute(null, 0, 10).get();

        assertThat(data.revealed()).isFalse();
        assertThat(data.entries()).isEmpty();
        assertThat(data.totalPlayers()).isEqualTo(42);
        verify(predictionRepo, never()).leaderboard(any(), anyInt(), anyInt());
    }

    @Test
    void revealsOnceAnyRowIsScored() {
        when(predictionRepo.countScoredBySeason(seasonId)).thenReturn(2);
        when(predictionRepo.countBySeason(seasonId)).thenReturn(2);
        when(predictionRepo.leaderboard(seasonId, 0, 10))
                .thenReturn(List.of(entry(1, "aaa", 350, 8, 0), entry(2, "bbb", 300, 5, 0)));

        var data = useCase.execute(null, 0, 10).get();

        assertThat(data.revealed()).isTrue();
        assertThat(data.entries()).hasSize(2);
        assertThat(data.totalEntries()).isEqualTo(2);
    }

    @Test
    void carriesTheViewersOwnStandingWhenSignedIn() {
        UUID userId = UUID.randomUUID();
        when(predictionRepo.countScoredBySeason(seasonId)).thenReturn(3);
        when(predictionRepo.countBySeason(seasonId)).thenReturn(3);
        when(predictionRepo.leaderboard(seasonId, 0, 10)).thenReturn(List.of(entry(1, "aaa", 350, 8, 0)));
        when(predictionRepo.userStanding(seasonId, userId)).thenReturn(Optional.of(entry(7, "me", 200, 2, 0)));

        var data = useCase.execute(userId, 0, 10).get();

        assertThat(data.userEntry()).isNotNull();
        assertThat(data.userEntry().position()).isEqualTo(7);
    }

    @Test
    void doesNotLookUpAStandingForAGuest() {
        when(predictionRepo.countScoredBySeason(seasonId)).thenReturn(1);
        when(predictionRepo.countBySeason(seasonId)).thenReturn(1);
        when(predictionRepo.leaderboard(seasonId, 0, 10)).thenReturn(List.of(entry(1, "aaa", 350, 8, 0)));

        var data = useCase.execute(null, 0, 10).get();

        assertThat(data.userEntry()).isNull();
        verify(predictionRepo, never()).userStanding(any(), any());
    }

    @Test
    void collapsesGenuineTiesToAnEqualDisplayedPosition() {
        // row_number() numbers 4 and 5 distinctly; tied on score, zeroes AND settle time they should
        // read as tied, and the next row resumes at its own number.
        when(predictionRepo.countScoredBySeason(seasonId)).thenReturn(4);
        when(predictionRepo.countBySeason(seasonId)).thenReturn(4);
        when(predictionRepo.leaderboard(seasonId, 0, 10))
                .thenReturn(List.of(
                        entry(1, "aaa", 350, 8, 0),
                        entry(2, "bbb", 300, 5, 60),
                        entry(3, "ccc", 300, 5, 60),
                        entry(4, "ddd", 250, 3, 0)));

        var data = useCase.execute(null, 0, 10).get();

        assertThat(data.displayPositions()).containsExactly("1", "=2", "=2", "4");
    }

    @Test
    void doesNotTieRowsThatMatchOnScoreButSettledAtDifferentTimes() {
        // The tiebreak genuinely separates them, so they must not read as tied.
        when(predictionRepo.countScoredBySeason(seasonId)).thenReturn(2);
        when(predictionRepo.countBySeason(seasonId)).thenReturn(2);
        when(predictionRepo.leaderboard(seasonId, 0, 10))
                .thenReturn(List.of(entry(1, "early", 300, 5, 0), entry(2, "late", 300, 5, 7200)));

        var data = useCase.execute(null, 0, 10).get();

        assertThat(data.displayPositions()).containsExactly("1", "2");
    }

    @Test
    void handlesAThreeWayTie() {
        when(predictionRepo.countScoredBySeason(seasonId)).thenReturn(4);
        when(predictionRepo.countBySeason(seasonId)).thenReturn(4);
        when(predictionRepo.leaderboard(seasonId, 0, 10))
                .thenReturn(List.of(
                        entry(1, "aaa", 300, 5, 0),
                        entry(2, "bbb", 300, 5, 0),
                        entry(3, "ccc", 300, 5, 0),
                        entry(4, "ddd", 100, 1, 0)));

        var data = useCase.execute(null, 0, 10).get();

        assertThat(data.displayPositions()).containsExactly("=1", "=1", "=1", "4");
    }

    @Test
    void reportsSeasonNotFoundWhenThereIsNoActiveSeason() {
        reset(seasonRepo);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());

        var result = useCase.execute(null, 0, 10);

        assertThat(result.isLeft()).isTrue();
    }
}
