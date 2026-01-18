package com.ligitabl.api.usecases.demoseeding;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.Comparator;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecases.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.usecases.round.finalizeround.FinalizeRoundResult;
import com.ligitabl.api.usecases.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.repo.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeedSeasonUseCase {

    private final SeedingConfigLoader configLoader;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final TeamRepo teamRepo;
    private final ContestRepo contestRepo;
    private final UserRepo userRepo;
    private final MatchRepo matchRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final EntryRepo entryRepo;
    private final StandingsRepo standingsRepo;
    private final FinalizeRoundUseCase finalizeRoundUseCase;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final Random random = new Random(42);

    @Transactional
    public Either<SeedingError, SeasonSeedResult> execute() {
        log.info("Starting season demoseeding");

        List<String> warnings = new ArrayList<>();

        return loadConfiguration().flatMap(config -> validatePrerequisites(config)
                .flatMap(context -> seedAllData(context, config, warnings)));
    }

    /**
     * Phase 1: Load configuration file
     */
    private Either<SeedingError, SeedingConfig> loadConfiguration() {
        try {
            return Either.right(configLoader.loadConfig());
        } catch (Exception e) {
            return Either.left(
                    new SeedingError.ConfigurationError("Failed to load demoseeding config: " + e.getMessage()));
        }
    }

    /**
     * Phase 2: Validate all prerequisites exist
     */
    private Either<SeedingError, ValidationContext> validatePrerequisites(SeedingConfig config) {
        return findCompetition(config.getCompetitionSlug())
                .flatMap(competition -> validateWithCompetition(config, competition));
    }

    private Either<SeedingError, ValidationContext> validateWithCompetition(
            SeedingConfig config, Competition competition) {
        return findSeason(competition.getId(), config.getSeasonSlug())
                .flatMap(season -> validateWithSeason(config, competition, season));
    }

    private Either<SeedingError, ValidationContext> validateWithSeason(
            SeedingConfig config, Competition competition, Season season) {
        return findRounds(season).flatMap(rounds -> validateWithRounds(config, competition, season, rounds));
    }

    private Either<SeedingError, ValidationContext> validateWithRounds(
            SeedingConfig config, Competition competition, Season season, List<Round> rounds) {
        return findTeams(season).flatMap(teams -> validateWithTeams(config, competition, season, rounds, teams));
    }

    private Either<SeedingError, ValidationContext> validateWithTeams(
            SeedingConfig config, Competition competition, Season season, List<Round> rounds, List<Team> teams) {
        return findDefaultContest(season)
                .flatMap(defaultContest ->
                        validateWithContest(config, competition, season, rounds, teams, defaultContest));
    }

    private Either<SeedingError, ValidationContext> validateWithContest(
            SeedingConfig config,
            Competition competition,
            Season season,
            List<Round> rounds,
            List<Team> teams,
            Contest defaultContest) {
        return findUsers(config)
                .map(users -> buildValidationContext(competition, season, rounds, teams, defaultContest, users));
    }

    private ValidationContext buildValidationContext(
            Competition competition,
            Season season,
            List<Round> rounds,
            List<Team> teams,
            Contest defaultContest,
            List<User> users) {
        log.info(
                "Validation complete: competition={}, season={}, rounds={}, teams={}, users={}",
                competition.getName(),
                season.getName(),
                rounds.size(),
                teams.size(),
                users.size());
        return new ValidationContext(competition, season, rounds, teams, defaultContest, users);
    }

    private Either<SeedingError, Competition> findCompetition(String slug) {
        return competitionRepo
                .findBySlug(slug)
                .map(Either::<SeedingError, Competition>right)
                .orElse(Either.left(new SeedingError.CompetitionNotFound(slug)));
    }

    private Either<SeedingError, Season> findSeason(UUID competitionId, String slug) {
        return seasonRepo
                .findBySlug(competitionId, slug)
                .map(Either::<SeedingError, Season>right)
                .orElse(Either.left(new SeedingError.SeasonNotFound(slug)));
    }

    private Either<SeedingError, List<Team>> findTeams(Season season) {
        List<String> teamCodes =
                season.getInitialRankings().stream().map(TeamRank::getCode).toList();

        List<Team> teams = new ArrayList<>();
        for (String code : teamCodes) {
            Optional<Team> teamOpt = teamRepo.findByCode(code);
            if (teamOpt.isEmpty()) {
                return Either.left(new SeedingError.TeamNotFound(code));
            }
            teams.add(teamOpt.get());
        }

        return Either.right(teams);
    }

    private Either<SeedingError, List<Round>> findRounds(Season season) {
        List<Round> rounds = roundRepo.findBySeasonIdOrderByPosition(season.getId());

        if (rounds.isEmpty()) {
            return Either.left(new SeedingError.NoRoundsFound(season.getName()));
        }

        if (rounds.size() != season.getMaxRounds()) {
            return Either.left(new SeedingError.RoundsNotFound(season.getName(), season.getMaxRounds(), rounds.size()));
        }

        return Either.right(rounds);
    }

    private Either<SeedingError, Contest> findDefaultContest(Season season) {
        return contestRepo
                .findById(season.getMainContestId())
                .map(Either::<SeedingError, Contest>right)
                .orElse(Either.left(new SeedingError.DefaultContestNotFound(season.getMainContestId())));
    }

    private Either<SeedingError, SeasonSeedResult> seedAllData(
            ValidationContext ctx, SeedingConfig config, List<String> warnings) {
        log.info(
                "Found: competition={}, season={}, rounds={}, teams={}",
                ctx.competition().getName(),
                ctx.season().getName(),
                ctx.rounds().size(),
                ctx.teams().size());

        int matchesSeeded = seedMatches(ctx.season(), ctx.rounds(), ctx.teams(), config);
        Map<UUID, SeasonPrediction> predictions = createPredictions(
                ctx.users(), ctx.season(), ctx.teams(), ctx.defaultContest().getId());
        int swapsSeeded = seedSwaps(predictions, ctx.rounds(), config);

        return finalizeCompletedRounds(ctx.season(), ctx.rounds(), config, warnings)
                .map(roundsFinalized -> new SeasonSeedResult(
                        ctx.season(),
                        ctx.users(),
                        ctx.defaultContest(),
                        predictions,
                        ctx.rounds().size(),
                        matchesSeeded,
                        swapsSeeded,
                        roundsFinalized,
                        warnings));
    }

    private Either<SeedingError, List<User>> findUsers(SeedingConfig config) {
        List<User> users = new ArrayList<>();

        for (SeedingConfig.DemoUser demoUser : config.getDemoUsers()) {
            Either<SeedingError, User> userResult = userRepo.findByEmail(Email.create(demoUser.getEmail()))
                    .map(Either::<SeedingError, User>right)
                    .orElse(Either.left(new SeedingError.UserNotFound(demoUser.getEmail())));

            if (userResult.isLeft()) {
                return Either.left(userResult.getLeft());
            }

            User user = userResult.get();
            users.add(user);
            log.info("User ready: {}", user.getEmail().value());
        }

        return Either.right(users);
    }

    private int seedMatches(Season season, List<Round> rounds, List<Team> teams, SeedingConfig config) {
        int matchesCreated = 0;
        LocalDate currentDate = season.getStartDate();

        List<List<MatchPairing>> seasonFixtures = generateSeasonFixtures(teams);

        for (int roundIndex = 0; roundIndex < rounds.size(); roundIndex++) {
            Round round = rounds.get(roundIndex);
            List<MatchPairing> roundFixtures = seasonFixtures.get(roundIndex);
            boolean isFinished = roundIndex < config.getFinishedRounds();

            LocalDate matchDate = currentDate;
            DayOfWeek dayOfWeek = matchDate.getDayOfWeek();

            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                matchDate = currentDate.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
            }

            boolean isSaturday = random.nextDouble() < 0.6;
            if (!isSaturday) {
                matchDate = matchDate.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
            }

            int matchesInRound = 0;
            for (MatchPairing pairing : roundFixtures) {
                boolean exists = matchRepo.existsBySeasonAndRoundAndTeams(
                        season.getId(),
                        round.getId(),
                        pairing.homeTeam().getId(),
                        pairing.awayTeam().getId());

                if (!exists) {
                    LocalTime kickoffTime = randomizeKickoffTime(
                            isSaturday ? LocalTime.of(12, 30) : LocalTime.of(14, 0),
                            isSaturday ? LocalTime.of(17, 30) : LocalTime.of(16, 30));

                    ZonedDateTime kickoff = ZonedDateTime.of(matchDate, kickoffTime, ZoneId.of("Europe/London"));

                    int clientId = round.getPosition() * 100 + (matchesInRound + 1);

                    Match match = Match.builder()
                            .clientId(clientId)
                            .homeTeamId(pairing.homeTeam().getId())
                            .awayTeamId(pairing.awayTeam().getId())
                            .seasonId(season.getId())
                            .matchday(round.getPosition())
                            .roundId(round.getId())
                            .kickOff(kickoff.toOffsetDateTime())
                            .venue(pairing.homeTeam().getName() + " Stadium")
                            .slug(pairing.homeTeam().getSlug() + "-vs-"
                                    + pairing.awayTeam().getSlug())
                            .wasPostponed(false)
                            .wasSuspended(false)
                            .build();

                    if (isFinished) {

                        Score score = generateRandomScore();
                        match.setStatus(MatchStatus.FINISHED);
                        match.setScore(score);
                    } else {
                        match.setStatus(MatchStatus.SCHEDULED);
                    }

                    matchRepo.save(match);
                    matchesCreated++;
                    matchesInRound++;
                }
            }

            currentDate = currentDate.plusWeeks(1);
        }

        log.info("Seeded {} matches", matchesCreated);
        return matchesCreated;
    }

    private Score generateRandomScore() {
        int homeGoals = random.nextInt(5); // 0-4
        int awayGoals = random.nextInt(5);
        return Score.builder().homeGoals(homeGoals).awayGoals(awayGoals).build();
    }

    private Map<UUID, SeasonPrediction> createPredictions(
            List<User> users, Season season, List<Team> teams, UUID defaultContestId) {
        Map<UUID, SeasonPrediction> predictions = new HashMap<>();

        for (User user : users) {
            // Check if prediction exists
            Optional<SeasonPrediction> existing = predictionRepo.findByUserAndSeason(user.getId(), season.getId());

            if (existing.isPresent()) {
                predictions.put(user.getId(), existing.get());
                log.warn("User {} already has prediction", user.getEmail());
                continue;
            }

            // Generate random rankings
            List<TeamRank> rankings = generateRandomRankings(teams);

            SeasonPrediction prediction = SeasonPrediction.builder()
                    .userId(user.getId())
                    .seasonId(season.getId())
                    .initialRankings(new ArrayList<>(rankings))
                    .currentRankings(new ArrayList<>(rankings))
                    .swaps(new ArrayList<>())
                    .lastSwapAt(null)
                    .atRoundNumber(1)
                    .build();

            prediction = predictionRepo.save(prediction);
            predictions.put(user.getId(), prediction);

            // Create entry in default contest
            Entry entry = Entry.builder()
                    .userId(user.getId())
                    .contestId(defaultContestId)
                    .joinedAt(clock.instant())
                    .build();
            entryRepo.save(entry);

            log.info("Created prediction for user {}", user.getEmail());
        }

        return predictions;
    }

    private int seedSwaps(Map<UUID, SeasonPrediction> predictions, List<Round> rounds, SeedingConfig config) {
        int totalSwaps = 0;

        for (SeasonPrediction prediction : predictions.values()) {
            List<TeamRank> currentRankings = new ArrayList<>(prediction.getInitialRankings());
            Instant lastSwapAt = null;

            for (int roundIndex = 0; roundIndex < config.getFinishedRounds(); roundIndex++) {
                Round round = rounds.get(roundIndex);

                // Get round's first match to find kickoff date
                List<Match> roundMatches = matchRepo.findByRoundId(round.getId());
                if (roundMatches.isEmpty()) continue;

                OffsetDateTime firstKickoff = roundMatches.stream()
                        .map(Match::getKickOff)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElseThrow();

                Instant firstKickoffInstant = firstKickoff.toInstant();

                // Generate 1-5 swaps
                int numSwaps = 1 + random.nextInt(5);

                for (int swapIndex = 0; swapIndex < numSwaps; swapIndex++) {
                    // Schedule swap on weekday, respecting 24h cooldown
                    Instant swapTime = generateWeekdaySwapTime(firstKickoffInstant, lastSwapAt, swapIndex, numSwaps);

                    if (swapTime.isAfter(firstKickoffInstant)) {
                        continue; // Skip if after kickoff
                    }

                    // Pick two random teams to swap
                    int idx1 = random.nextInt(currentRankings.size());
                    int idx2 = random.nextInt(currentRankings.size());
                    while (idx1 == idx2) {
                        idx2 = random.nextInt(currentRankings.size());
                    }

                    TeamRank team1 = currentRankings.get(idx1);
                    TeamRank team2 = currentRankings.get(idx2);

                    // Swap positions
                    TeamRank newTeam1 = team1.withPosition(team2.getPosition());
                    TeamRank newTeam2 = team2.withPosition(team1.getPosition());

                    currentRankings.set(idx1, newTeam1);
                    currentRankings.set(idx2, newTeam2);

                    // Record swap
                    SwapChange change = new SwapChange(
                            swapTime,
                            String.format("%s:%d→%d", team1.getCode(), team1.getPosition(), newTeam1.getPosition()),
                            String.format("%s:%d→%d", team2.getCode(), team2.getPosition(), newTeam2.getPosition()));

                    prediction.addSwap(round.getPosition(), change);
                    lastSwapAt = swapTime;
                    totalSwaps++;
                }

                // Update prediction state for this round
                prediction.setCurrentRankings(new ArrayList<>(currentRankings));
                prediction.setLastSwapAt(lastSwapAt);
                prediction.setAtRoundNumber(round.getPosition());
            }

            predictionRepo.save(prediction);
        }

        log.info("Seeded {} total swaps across all users", totalSwaps);
        return totalSwaps;
    }

    private Instant generateWeekdaySwapTime(Instant roundKickoff, Instant lastSwapAt, int swapIndex, int totalSwaps) {
        // Calculate the week before kickoff
        Instant weekBefore = roundKickoff.minus(Duration.ofDays(7));

        // Distribute swaps evenly across weekdays
        long millisPerSwap = Duration.ofDays(5).toMillis() / totalSwaps; // 5 weekdays
        Instant baseTime = weekBefore.plusMillis(swapIndex * millisPerSwap);

        // Ensure weekday
        ZonedDateTime zdt = baseTime.atZone(ZoneId.of("Europe/London"));
        while (zdt.getDayOfWeek() == DayOfWeek.SATURDAY || zdt.getDayOfWeek() == DayOfWeek.SUNDAY) {
            zdt = zdt.plusDays(1);
        }

        // Respect 24h cooldown
        if (lastSwapAt != null) {
            Instant earliest = lastSwapAt.plus(Duration.ofHours(24));
            if (zdt.toInstant().isBefore(earliest)) {
                zdt = earliest.atZone(ZoneId.of("Europe/London"));
            }
        }

        return zdt.toInstant();
    }

    private Either<SeedingError, Integer> finalizeCompletedRounds(
            Season season, List<Round> rounds, SeedingConfig config, List<String> warnings) {
        int finalized = 0;

        for (int i = 0; i < config.getFinishedRounds(); i++) {
            Either<FinalizeRoundError, FinalizeRoundResult> result = finalizeRoundUseCase.execute(season.getId());

            if (result.isLeft()) {
                FinalizeRoundError error = result.getLeft();
                String errorMessage = formatFinalizeRoundError(error);

                warnings.add(errorMessage);
                log.warn("Failed to finalize round {}: {}", i + 1, errorMessage);

                // For critical errors, stop and return error
                if (isCriticalFinalizeRoundError(error)) {
                    return Either.left(new SeedingError.FinalizationFailed(i + 1, errorMessage));
                }

                break; // Stop on first failure but don't fail entire demoseeding
            } else {
                finalized++;
            }
        }

        log.info("Finalized {} rounds", finalized);
        return Either.right(finalized);
    }

    // Helper methods
    private String formatFinalizeRoundError(FinalizeRoundError error) {
        return switch (error) {
            case FinalizeRoundError.RoundNotReady e -> String.format("Round %s not ready: %s", e.roundId(), e.reason());
            case FinalizeRoundError.StandingsValidationFailed e -> String.format(
                    "Standings validation failed: %s", e.reason());
            case FinalizeRoundError.TransactionFailed e -> String.format("Transaction failed: %s", e.reason());
            default -> "Unexpected finalize-round error: " + error;
        };
    }

    private boolean isCriticalFinalizeRoundError(FinalizeRoundError error) {
        // Treat validation failures as critical
        return error instanceof FinalizeRoundError.StandingsValidationFailed;
    }

    private List<List<MatchPairing>> generateSeasonFixtures(List<Team> teams) {
        List<List<MatchPairing>> seasonFixtures = new ArrayList<>();
        List<List<MatchPairing>> firstHalf = generateRoundRobin(teams);
        seasonFixtures.addAll(firstHalf);

        for (List<MatchPairing> round : firstHalf) {
            List<MatchPairing> reversedRound = round.stream()
                    .map(m -> new MatchPairing(m.awayTeam(), m.homeTeam()))
                    .toList();
            seasonFixtures.add(reversedRound);
        }

        return seasonFixtures;
    }

    private List<List<MatchPairing>> generateRoundRobin(List<Team> teams) {
        // Circle algorithm (same as before)
        List<List<MatchPairing>> rounds = new ArrayList<>();
        List<Team> teamList = new ArrayList<>(teams);
        int numTeams = teamList.size();
        int numRounds = numTeams - 1;

        for (int round = 0; round < numRounds; round++) {
            List<MatchPairing> matches = new ArrayList<>();

            for (int match = 0; match < numTeams / 2; match++) {
                int home = (round + match) % (numRounds);
                int away = (numRounds - match + round) % (numRounds);

                if (match == 0) {
                    away = numTeams - 1;
                }

                matches.add(new MatchPairing(teamList.get(home), teamList.get(away)));
            }

            rounds.add(matches);
        }

        return rounds;
    }

    private List<TeamRank> generateRandomRankings(List<Team> teams) {
        List<Team> shuffled = new ArrayList<>(teams);
        Collections.shuffle(shuffled, random);

        List<TeamRank> rankings = new ArrayList<>();
        for (int i = 0; i < shuffled.size(); i++) {
            rankings.add(new TeamRank(shuffled.get(i).getCode(), i + 1));
        }

        return rankings;
    }

    private LocalTime randomizeKickoffTime(LocalTime start, LocalTime end) {
        long startSeconds = start.toSecondOfDay();
        long endSeconds = end.toSecondOfDay();
        long randomSeconds = startSeconds + random.nextInt((int) (endSeconds - startSeconds));
        randomSeconds = (randomSeconds / 900) * 900; // Round to 15 min
        return LocalTime.ofSecondOfDay(randomSeconds);
    }

    private record MatchPairing(Team homeTeam, Team awayTeam) {}
}
