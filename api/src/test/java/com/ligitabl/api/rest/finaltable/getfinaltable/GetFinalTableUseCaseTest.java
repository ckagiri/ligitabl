package com.ligitabl.api.rest.finaltable.getfinaltable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.config.FinalTableDevProperties;
import com.ligitabl.api.rest.finaltable.shared.FinalTableRowsJson;
import com.ligitabl.api.rest.finaltable.shared.FinalTableSupport;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.api.web.shared.share.SharePredictionTextBuilder;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;
import com.ligitabl.model.repo.TeamRepo;

@ExtendWith(MockitoExtension.class)
class GetFinalTableUseCaseTest {

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

    @Mock
    private TeamRepo teamRepo;

    @Mock
    private CompetitionRepo competitionRepo;

    @Mock
    private StandingsRepo standingsRepo;

    private static final Instant NOW = TestCalendar.MID_SEASON;
    private static final List<TeamRank> BASELINE =
            List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3));

    private GetFinalTableUseCase useCase;
    private UUID seasonId;
    private UUID roundId;
    private Season season;

    @BeforeEach
    void setUp() {
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        season = Season.builder()
                .id(seasonId)
                .slug(SeasonSlug.of(TestCalendar.SEASON_SLUG))
                .completed(false)
                .initialRankings(BASELINE)
                .maxHitPoints(8)
                .build();

        RoundSupport roundSupport = new RoundSupport(roundRepo, matchRepo, hierarchyValidator, competitionDefaults);
        FinalTableDevProperties devProperties = new FinalTableDevProperties();

        useCase = new GetFinalTableUseCase(
                predictionRepo,
                new FinalTableSupport(competitionDefaults, seasonRepo, roundRepo, roundSupport),
                teamRepo,
                new SharePredictionTextBuilder(),
                devProperties,
                new FinalTableRowsJson(),
                competitionRepo,
                standingsRepo);
        ReflectionTestUtils.setField(useCase, "frontendShareUrl", "https://ligipredictor.test");

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        lenient().when(teamRepo.findAllTeamsByCode(any())).thenReturn(Map.of());
        lenient().when(competitionRepo.findById(any())).thenReturn(Optional.empty());
        lenient()
                .when(roundRepo.findBySeasonIdOrderByPosition(seasonId))
                .thenReturn(List.of(Round.builder()
                        .id(roundId)
                        .seasonId(seasonId)
                        .position(1)
                        .finalized(false)
                        .build()));
        lenient().when(matchRepo.findByRoundId(roundId)).thenReturn(List.of());
    }

    private FinalTablePrediction row() {
        return FinalTablePrediction.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .seasonId(seasonId)
                .rankings(new ArrayList<>(List.of(TeamRank.of("LIV", 1), TeamRank.of("ARS", 2), TeamRank.of("MCI", 3))))
                .settledAt(NOW)
                .build();
    }

    @Test
    void aGuestSeesTheBaselineTableReadOnly() {
        // The game is an on-ramp, so a guest gets a real table rather than an empty state.
        var data = useCase.execute(null, null, null).get();

        assertThat(data.isGuest()).isTrue();
        assertThat(data.hasEntry()).isFalse();
        assertThat(data.rankings()).containsExactlyElementsOf(BASELINE);
        assertThat(data.revealed()).isFalse();
        verify(predictionRepo, never()).findByUserAndSeason(any(), any());
    }

    @Test
    void aSignedInUserWithNoRowAlsoStartsFromTheBaseline() {
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.empty());

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.isGuest()).isFalse();
        assertThat(data.hasEntry()).isFalse();
        assertThat(data.rankings()).containsExactlyElementsOf(BASELINE);
        assertThat(data.entryOpen()).isTrue();
    }

    @Test
    void aSavedTableIsShownInPositionOrder() {
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.hasEntry()).isTrue();
        assertThat(data.expectedOrder()).containsExactly("LIV", "ARS", "MCI");
    }

    @Test
    void anUnscoredRowIsNotRevealedAndCarriesNoScores() {
        UUID userId = UUID.randomUUID();
        FinalTablePrediction prediction = row();
        // Even if score columns were somehow populated, an unscored row must not leak them.
        prediction.setTotalScore(425);
        prediction.setZeroesCount(20);
        prediction.setChampionBonus(25);
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.revealed()).isFalse();
        assertThat(data.totalScore()).isNull();
        assertThat(data.zeroesCount()).isNull();
        assertThat(data.championBonus()).isNull();
        assertThat(data.resultRankings()).isNull();
    }

    @Test
    void aScoredRowIsRevealedWithItsScores() {
        // Reveal follows the row being scored, not the season being complete.
        UUID userId = UUID.randomUUID();
        FinalTablePrediction prediction = row();
        prediction.setScoredAt(NOW);
        prediction.setBaseScore(6);
        prediction.setZeroesCount(2);
        prediction.setBonusPoints(20);
        prediction.setChampionBonus(25);
        prediction.setTotalScore(51);
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.revealed()).isTrue();
        assertThat(data.totalScore()).isEqualTo(51);
        assertThat(data.baseScore()).isEqualTo(6);
        assertThat(data.bonusPoints()).isEqualTo(20);
        assertThat(data.championBonus()).isEqualTo(25);
    }

    @Test
    void entryIsClosedOnceTheSeasonIsCompletedEvenThoughThePageStillRenders() {
        // The waiting/result page must stay readable for nine months; only editing closes.
        season.setCompleted(true);
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.entryOpen()).isFalse();
        assertThat(data.rankings()).hasSize(3);
        assertThat(data.roundStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void buildsAShareUrlAndTextForASignedInUser() {
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        // Shorthand, and specifically not the SeasonSlug record's toString: interpolating that
        // yields ".../SeasonSlug[value=2026-27]" and every share link is broken.
        assertThat(data.shareUrl())
                .isEqualTo("https://ligipredictor.test/final-table/u/abc123/"
                        + SeasonSlug.of(TestCalendar.SEASON_SLUG).toShorthand());
        assertThat(data.shareUrl()).doesNotContain("SeasonSlug");
        assertThat(data.shareText()).contains("Final Table prediction");
        assertThat(data.shareText()).contains("Shared before season kickoff");
        assertThat(data.shareText()).doesNotContain("Gameweek");
    }

    @Test
    void buildsAShareUrlBeforeTheFirstSaveSoTheCardCanAppearWithoutAReload() {
        // The URL is knowable before a row exists, and saving is a client-side POST that never
        // re-renders the page — so it has to be on hand for the moment the first save lands.
        // Whether the card is *shown* is the client's call, gated on hasEntry.
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.empty());

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.hasEntry()).isFalse();
        assertThat(data.shareUrl()).contains("/final-table/u/abc123/");
    }

    @Test
    void omitsShareLinksForAGuestWhoHasNothingToShare() {
        var data = useCase.execute(null, null, null).get();

        assertThat(data.shareUrl()).isNull();
        assertThat(data.shareText()).isNull();
    }

    @Test
    void devPreviewIsOffByDefault() {
        var data = useCase.execute(null, null, null).get();

        assertThat(data.devPreviewEnabled()).isFalse();
    }

    // --- owner name for the share card ----------------------------------------------------

    @Test
    void carriesTheOwnersNameForTheShareCard() {
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.ownerName()).isEqualTo("Foobar");
    }

    @Test
    void cleansMarkupOutOfTheOwnersName() {
        // Stored names are validated for length only, so markup is already in the data. It is inert
        // in every render path (Thymeleaf escapes), but it would be painted verbatim onto the card.
        // Legible text is salvaged rather than blanked, so the card can still be personalised.
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));

        var data =
                useCase.execute(userId, "abc123", "<script>alert(1)</script>").get();

        assertThat(data.ownerName()).isEqualTo("alert 1");
    }

    @Test
    void hasNoOwnerNameWhenNothingLegibleSurvives() {
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));

        var data = useCase.execute(userId, "abc123", "<script></script>").get();

        assertThat(data.ownerName()).isNull();
    }

    @Test
    void hasNoOwnerNameForAGuest() {
        var data = useCase.execute(null, null, null).get();

        assertThat(data.ownerName()).isNull();
    }

    // --- live progress: locked, not yet scored, standings exist ---------------------------

    /** Closes entry and puts a standings table behind the season, i.e. mid-season. */
    private void lockedWithStandings(List<StandingsTeamRank> rankings) {
        season.setCompleted(true);
        when(standingsRepo.findLatestBySeason(seasonId))
                .thenReturn(Optional.of(Standings.builder()
                        .id(UUID.randomUUID())
                        .seasonId(seasonId)
                        .rankings(rankings)
                        .build()));
    }

    private static StandingsTeamRank standing(String code, int position) {
        return StandingsTeamRank.builder()
                .ranking(TeamRank.of(code, position))
                .metadata(StandingsMetadata.builder().build())
                .build();
    }

    @Test
    void showsLiveProgressOnceTheTableIsLockedAndStandingsExist() {
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));
        // Predicted LIV 1, ARS 2, MCI 3; actually ARS 1, MCI 2, LIV 3.
        lockedWithStandings(List.of(standing("ARS", 1), standing("MCI", 2), standing("LIV", 3)));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.liveProgress()).isTrue();
        assertThat(data.liveRowsJson())
                .contains("\"code\":\"LIV\"", "\"current\":3")
                .contains("\"code\":\"ARS\"", "\"current\":1");
    }

    @Test
    void liveProgressNeverCarriesAScore() {
        // The whole point of the state: position comparison, no score. A total, a zeroes count or
        // any aggregate here would be the reveal arriving nine months early.
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));
        lockedWithStandings(List.of(standing("ARS", 1), standing("MCI", 2), standing("LIV", 3)));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.liveProgress()).isTrue();
        assertThat(data.revealed()).isFalse();
        assertThat(data.totalScore()).isNull();
        assertThat(data.baseScore()).isNull();
        assertThat(data.zeroesCount()).isNull();
        assertThat(data.bonusPoints()).isNull();
        assertThat(data.resultRankings()).isNull();
        assertThat(data.liveRowsJson()).doesNotContain("score", "hit", "zero", "bonus");
    }

    @Test
    void renderingLiveProgressNeverWritesToThePredictionRow() {
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));
        lockedWithStandings(List.of(standing("ARS", 1), standing("MCI", 2), standing("LIV", 3)));

        useCase.execute(userId, "abc123", "Foobar");

        // A GET must never take the StandingsSource.CURRENT path, which persists and reveals.
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void liveProgressIsOffWhileEntryIsStillOpen() {
        // Pre-lock the player is still editing; a "now" column would just be noise.
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.entryOpen()).isTrue();
        assertThat(data.liveProgress()).isFalse();
        assertThat(data.liveRowsJson()).isEqualTo("[]");
        verify(standingsRepo, never()).findLatestBySeason(any());
    }

    @Test
    void liveProgressIsOffBeforeAnyStandingsExist() {
        // Locked at GW1 but nothing played yet: fall back to the "results at season end" copy
        // rather than a table of dashes.
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));
        season.setCompleted(true);
        when(standingsRepo.findLatestBySeason(seasonId)).thenReturn(Optional.empty());

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.liveProgress()).isFalse();
        assertThat(data.liveRowsJson()).isEqualTo("[]");
    }

    @Test
    void liveProgressIsOffOnceTheRowIsScored() {
        // Revealed wins: the result table is authoritative, so there is nothing provisional to show.
        UUID userId = UUID.randomUUID();
        FinalTablePrediction prediction = row();
        prediction.setScoredAt(NOW);
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        season.setCompleted(true);

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.revealed()).isTrue();
        assertThat(data.liveProgress()).isFalse();
        verify(standingsRepo, never()).findLatestBySeason(any());
    }

    @Test
    void liveProgressIsOffForAGuest() {
        season.setCompleted(true);

        var data = useCase.execute(null, null, null).get();

        assertThat(data.liveProgress()).isFalse();
        verify(standingsRepo, never()).findLatestBySeason(any());
    }

    @Test
    void aTeamMissingFromStandingsSimplyHasNoReading() {
        // Partial standings must degrade per row, not fail the page.
        UUID userId = UUID.randomUUID();
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row()));
        lockedWithStandings(List.of(standing("ARS", 1), standing("LIV", 2)));

        var data = useCase.execute(userId, "abc123", "Foobar").get();

        assertThat(data.liveProgress()).isTrue();
        // MCI is in the prediction but not the standings, so it carries no `current`.
        assertThat(data.liveRowsJson()).contains("\"code\":\"MCI\",\"name\"");
        assertThat(data.liveRowsJson())
                .doesNotContain("\"code\":\"MCI\",\"name\":\"MCI\",\"shortName\":\"MCI\",\"current\"");
    }
}
