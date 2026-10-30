package com.ligitabl.api.usecase.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.api.usecase.prediction.getprediction.GetPredictionUseCase;
import com.ligitabl.api.usecase.prediction.getprediction.PredictionRankEnricher;
import com.ligitabl.api.usecase.prediction.getuserprediction.GetUserPredictionQuery;
import com.ligitabl.api.usecase.prediction.getuserprediction.GetUserPredictionUseCase;
import com.ligitabl.api.usecase.prediction.getuserprediction.PreviewRankingsSupport;
import com.ligitabl.api.usecase.prediction.makeswap.MakeSwapUseCase;
import com.ligitabl.api.usecase.prediction.makeswap.SwapCommand;
import com.ligitabl.api.usecase.prediction.roundopeningswap.RoundOpeningSwapCommand;
import com.ligitabl.api.usecase.prediction.roundopeningswap.RoundOpeningSwapUseCase;
import com.ligitabl.api.usecase.prediction.shared.SwapHelper;
import com.ligitabl.api.usecase.round.shared.RoundSupport;
import com.ligitabl.api.usecase.shared.HierarchyValidator;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

/**
 * Records what each of the four "can this user swap?" checks answers today, for the same
 * prediction state. Rows where they disagree are task 513's drift list; PR A replaces the four
 * with {@code SeasonPrediction.swapEligibility} and changes only the rows decided there.
 *
 * <p>Columns: {@code MakeSwapUseCase}, {@code RoundOpeningSwapUseCase}, the prediction page's
 * access mode ({@code GetUserPredictionUseCase}) and REST {@code can_swap}
 * ({@code GetPredictionUseCase}). A rejection is the {@code SwapError} or blocked reason.
 *
 * <p>The page column is the use case's access mode, before {@code UserPredictionsController} ANDs
 * it with "round open, or the prediction is for a future round". So EDITABLE on a LOCKED row
 * renders read-only, except for joined N+1.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SwapEligibilityCharacterisationTest {

    private static final int N = 10;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");
    private final Instant now = TestCalendar.MID_SEASON;
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final List<TeamRank> rankings = List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2));

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private SeasonPredictionRepo predictionRepo;

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private CompetitionRepo competitionRepo;

    @Mock
    private RoundResultRepo roundResultRepo;

    @Mock
    private StandingsRepo standingsRepo;

    @Mock
    private EntryRepo entryRepo;

    @Mock
    private PredictionRankEnricher rankEnricher;

    private final UUID userId = UUID.randomUUID();
    private final UUID seasonId = UUID.randomUUID();
    private final UUID roundId = UUID.randomUUID();
    private final UUID nextRoundId = UUID.randomUUID();

    private MakeSwapUseCase makeSwap;
    private RoundOpeningSwapUseCase openingSwap;
    private GetUserPredictionUseCase page;
    private GetPredictionUseCase rest;

    @BeforeEach
    void setUp() {
        RoundSupport roundSupport = new RoundSupport(roundRepo, matchRepo, hierarchyValidator, competitionDefaults);
        SwapHelper swapHelper =
                new SwapHelper(competitionDefaults, seasonRepo, roundRepo, predictionRepo, roundSupport, clock);

        makeSwap = new MakeSwapUseCase(roundRepo, predictionRepo, clock, swapHelper);
        openingSwap = new RoundOpeningSwapUseCase(predictionRepo, clock, swapHelper);
        page = new GetUserPredictionUseCase(
                competitionDefaults,
                competitionRepo,
                predictionRepo,
                seasonRepo,
                roundRepo,
                roundResultRepo,
                standingsRepo,
                matchRepo,
                entryRepo,
                new PreviewRankingsSupport(seasonRepo, standingsRepo),
                clock);
        rest = new GetPredictionUseCase(
                competitionDefaults,
                seasonRepo,
                roundRepo,
                roundSupport,
                predictionRepo,
                standingsRepo,
                rankEnricher,
                clock);

        Season season = Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .maxRounds(38)
                .completed(false)
                .mainContestId(UUID.randomUUID())
                .initialRankings(rankings)
                .build();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round(roundId, N)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, N + 1)).thenReturn(Optional.of(round(nextRoundId, N + 1)));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * Current round is N. {@code at}/{@code opening} are {@code atRoundNumber} and
     * {@code openingCommittedRound}; {@code lastSwap} is how long ago, or {@code none}.
     *
     * <pre>
     * path            at   opening  how it arises
     * pre-season      0    0        registered off-season, not merged yet
     * legacy          N-1  0        row predating openingCommittedRound, backfilled to 0
     * joined N-1      N-1  N-1      joined, merged or opening-swapped at N-1, nothing since
     * joined N        N    N        joined, merged or opening-swapped at N
     * free swap N     N    N-1      joined at N-1 with no swaps, then the free swap at N
     * joined N+1      N+1  N+1      joined while N was locked; only reachable with N not open
     * </pre>
     */
    @ParameterizedTest(name = "{0} lastSwap={3} {4} -> {5}")
    @CsvSource({
        // path,         at, opening, lastSwap, round,  make | opening | page | rest
        "pre-season,      0,  0,      none,     OPEN,   'OK | PreSeasonMergeRequired | EDITABLE | true'",
        "pre-season,      0,  0,      none,     LOCKED, 'RoundNotOpen | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
        "legacy,          9,  0,      none,     OPEN,   'OK | OK | EDITABLE | true'",
        "legacy,          9,  0,      1h,       OPEN,   'UseOpeningWindowFirst | OK | EDITABLE | COOLDOWN_ACTIVE'",
        "legacy,          9,  0,      25h,      OPEN,   'UseOpeningWindowFirst | OK | EDITABLE | true'",
        "legacy,          9,  0,      none,     LOCKED, 'RoundNotOpen | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
        "legacy,          9,  0,      1h,       LOCKED, 'RoundNotOpen | RoundNotOpen | READONLY | ROUND_NOT_OPEN'",
        "legacy,          9,  0,      25h,      LOCKED, 'RoundNotOpen | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
        "joined N-1,      9,  9,      none,     OPEN,   'OK | OK | EDITABLE | true'",
        "joined N-1,      9,  9,      1h,       OPEN,   'UseOpeningWindowFirst | OK | EDITABLE | COOLDOWN_ACTIVE'",
        "joined N-1,      9,  9,      25h,      OPEN,   'UseOpeningWindowFirst | OK | EDITABLE | true'",
        "joined N-1,      9,  9,      none,     LOCKED, 'RoundNotOpen | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
        "joined N-1,      9,  9,      1h,       LOCKED, 'RoundNotOpen | RoundNotOpen | READONLY | ROUND_NOT_OPEN'",
        "joined N-1,      9,  9,      25h,      LOCKED, 'RoundNotOpen | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
        "joined N,       10, 10,      none,     OPEN,   'OK | OpeningAlreadyUsed | EDITABLE | true'",
        "joined N,       10, 10,      1h,       OPEN,   'CooldownActive | OpeningAlreadyUsed | READONLY | COOLDOWN_ACTIVE'",
        "joined N,       10, 10,      25h,      OPEN,   'OK | OpeningAlreadyUsed | EDITABLE | true'",
        "joined N,       10, 10,      none,     LOCKED, 'RoundNotOpen | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
        "joined N,       10, 10,      1h,       LOCKED, 'RoundNotOpen | RoundNotOpen | READONLY | ROUND_NOT_OPEN'",
        "joined N,       10, 10,      25h,      LOCKED, 'RoundNotOpen | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
        "free swap N,    10,  9,      1h,       OPEN,   'UseOpeningWindowFirst | OpeningAlreadyUsed | EDITABLE | COOLDOWN_ACTIVE'",
        "free swap N,    10,  9,      25h,      OPEN,   'UseOpeningWindowFirst | OpeningAlreadyUsed | EDITABLE | true'",
        "free swap N,    10,  9,      1h,       LOCKED, 'RoundNotOpen | RoundNotOpen | READONLY | ROUND_NOT_OPEN'",
        "free swap N,    10,  9,      25h,      LOCKED, 'RoundNotOpen | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
        "joined N+1,     11, 11,      none,     LOCKED, 'OK | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
        "joined N+1,     11, 11,      1h,       LOCKED, 'CooldownActive | RoundNotOpen | READONLY | ROUND_NOT_OPEN'",
        "joined N+1,     11, 11,      25h,      LOCKED, 'OK | RoundNotOpen | EDITABLE | ROUND_NOT_OPEN'",
    })
    void recordsTodaysAnswer(String path, int at, int opening, String lastSwap, String roundStatus, String expected) {
        Instant lastSwapAt =
                switch (lastSwap) {
                    case "none" -> null;
                    case "1h" -> now.minus(Duration.ofHours(1));
                    case "25h" -> now.minus(Duration.ofHours(25));
                    default -> throw new IllegalArgumentException(lastSwap);
                };
        when(predictionRepo.findByUserAndSeason(userId, seasonId))
                .thenAnswer(i -> Optional.of(prediction(at, opening, lastSwapAt)));
        stubRoundStatus(roundStatus);

        String actual = String.join(" | ", makeSwap(), openingSwap(), page(), rest());

        assertEquals(expected, actual);
    }

    private String makeSwap() {
        return makeSwap.execute(userId, new SwapCommand("ARS", "LIV"))
                .fold(error -> error.getClass().getSimpleName(), ok -> "OK");
    }

    private String openingSwap() {
        return openingSwap
                .execute(userId, new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV"))))
                .fold(error -> error.getClass().getSimpleName(), ok -> "OK");
    }

    private String page() {
        return page.execute(GetUserPredictionQuery.forAuthenticatedUser(userId, seasonId, true, null))
                .fold(error -> "error: " + error, data -> data.accessMode().name());
    }

    private String rest() {
        return rest.execute(userId).fold(error -> "error: " + error, result -> {
            var status = result.swapStatus();
            return status.canSwap() ? "true" : status.blockedReason();
        });
    }

    /** OPEN is the no-matches default; LOCKED is a live match in round N. Round N+1 stays OPEN. */
    private void stubRoundStatus(String status) {
        List<Match> matches =
                switch (status) {
                    case "OPEN" -> List.of();
                    case "LOCKED" -> List.of(Match.builder()
                            .id(UUID.randomUUID())
                            .status(MatchStatus.LIVE)
                            .build());
                    default -> throw new IllegalArgumentException(status);
                };
        when(matchRepo.findByRoundId(roundId)).thenReturn(matches);
        when(matchRepo.findBySeasonAndRound(seasonId, N))
                .thenReturn(matches.isEmpty() ? Map.of() : Map.of("ARS", matches, "LIV", matches));
    }

    private Round round(UUID id, int position) {
        return Round.builder()
                .id(id)
                .seasonId(seasonId)
                .position(position)
                .finalized(false)
                .name("Round " + position)
                .slug("round-" + position)
                .build();
    }

    // Fresh per call: the swap use cases mutate the prediction they load.
    private SeasonPrediction prediction(int at, int opening, Instant lastSwapAt) {
        return SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(rankings)
                .currentRankings(rankings)
                .swaps(new ArrayList<>())
                .lastSwapAt(lastSwapAt)
                .atRoundNumber(at)
                .openingCommittedRound(opening)
                .build();
    }
}
