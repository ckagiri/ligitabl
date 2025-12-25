package com.ligitabl.api.usecases.prediction.seeding;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecases.prediction.finalizeround.FinalizationError;
import com.ligitabl.api.usecases.prediction.finalizeround.FinalizationResult;
import com.ligitabl.api.usecases.prediction.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.model.SwapChange;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

// application/seeding/SeedSeasonUseCase.java
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
    public Either<String, SeasonSeedResult> execute() {
        log.info("Starting simplified season seeding");

        List<String> warnings = new ArrayList<>();

        try {
            // Load configuration
            SeedingConfig config = configLoader.loadConfig();

            // FIND existing entities (throw if not found)
            Competition competition = findCompetition(config.getCompetitionSlug());
            Season season = findSeason(competition.getId(), config.getSeasonSlug());
            List<Round> rounds = findRounds(season);
            List<Team> teams = findTeams(season);
            Contest defaultContest = findDefaultContest(season);

            log.info("Found: competition={}, season={}, rounds={}, teams={}",
                    competition.getName(), season.getName(), rounds.size(), teams.size());

            // CREATE only what's needed
            int matchesSeeded = seedMatches(season, rounds, teams, config);
            List<User> users = findUsers(config);
            Map<Long, SeasonPrediction> predictions = createPredictions(users, season, teams);
            int swapsSeeded = seedSwaps(predictions, rounds, config);
            int roundsFinalized = finalizeCompletedRounds(season, rounds, config, warnings);

            log.info("Seeding completed successfully");

            return Either.right(new SeasonSeedResult(
                    season,
                    users,
                    defaultContest,
                    predictions,
                    rounds.size(),
                    matchesSeeded,
                    swapsSeeded,
                    roundsFinalized,
                    warnings
            ));

        } catch (Exception e) {
            log.error("Seeding failed", e);
            return Either.left("Seeding failed: " + e.getMessage());
        }
    }

    // FIND methods (throw if not found)

    private Competition findCompetition(String slug) {
        return competitionRepo.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException(
                        "Competition not found: " + slug + ". Please create it first."
                ));
    }

    private Season findSeason(UUID competitionId, String slug) {
        return seasonRepo.findBySlug(competitionId, slug)
                .orElseThrow(() -> new IllegalStateException(
                        "Season not found: " + slug + ". Please create it first."
                ));
    }

    private List<Round> findRounds(Season season) {
        List<Round> rounds = roundRepo.findBySeasonIdOrderByPosition(season.getId());

        if (rounds.isEmpty()) {
            throw new IllegalStateException(
                    "No rounds found for season: " + season.getName() + ". Please create them first."
            );
        }

        if (rounds.size() != season.getMaxRounds()) {
            throw new IllegalStateException(
                    String.format("Expected %d rounds but found %d for season: %s",
                            season.getMaxRounds(), rounds.size(), season.getName())
            );
        }

        return rounds;
    }

    private List<Team> findTeams(Season season) {
        List<String> teamCodes = season.getInitialRankings().stream()
                .map(TeamRank::getCode)
                .toList();

        List<Team> teams = new ArrayList<>();
        for (String code : teamCodes) {
            Team team = teamRepo.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException(
                            "Team not found: " + code + ". Please create it first."
                    ));
            teams.add(team);
        }

        return teams;
    }

    private Contest findDefaultContest(Season season) {
        return contestRepo.findById(season.getMainContestId())
                .orElseThrow(() -> new IllegalStateException(
                        "Default contest not found for season: " + season.getName()
                ));
    }

    // CREATE methods (find or create)

    private List<User> findUsers(SeedingConfig config) {
        List<User> users = new ArrayList<>();

        for (SeedingConfig.DemoUser demoUser : config.getDemoUsers()) {
            User user = userRepo.findByEmail(Email.create(demoUser.getEmail()))
                    .orElseThrow();

            users.add(user);
            log.info("User ready: {}", user.getEmail());
        }

        return users;
    }

    private int seedMatches(
            Season season,
            List<Round> rounds,
            List<Team> teams,
            SeedingConfig config
    ) {
        int matchesCreated = 0;
        LocalDate currentDate = season.getStartDate();

        List<List<MatchPairing>> seasonFixtures = generateSeasonFixtures(teams, season.getMaxRounds());

        for (int roundIndex = 0; roundIndex < rounds.size(); roundIndex++) {
            Round round = rounds.get(roundIndex);
            List<MatchPairing> roundFixtures = seasonFixtures.get(roundIndex);
            boolean isFinished = roundIndex < config.getFinishedRounds();

            // Determine match date
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
                // Check if match exists
                boolean exists = matchRepo.existsBySeasonAndRoundAndTeams(
                        season.getId(),
                        round.getId(),
                        pairing.homeTeam().getId(),
                        pairing.awayTeam().getId()
                );

                if (!exists) {
                    LocalTime kickoffTime = randomizeKickoffTime(
                            isSaturday ? LocalTime.of(12, 30) : LocalTime.of(14, 0),
                            isSaturday ? LocalTime.of(17, 30) : LocalTime.of(16, 30)
                    );

                    ZonedDateTime kickoff = ZonedDateTime.of(
                            matchDate,
                            kickoffTime,
                            ZoneId.of("Europe/London")
                    );

                    Match match = Match.builder()
                            .clientId("SEED-" + round.getPosition() + "-" + matchesInRound)
                            .name(pairing.homeTeam().getName() + " vs " + pairing.awayTeam().getName())
                            .homeTeamId(pairing.homeTeam().getId())
                            .awayTeamId(pairing.awayTeam().getId())
                            .matchday(round.getPosition())
                            .roundId(round.getId())
                            .roundPosition(matchesInRound + 1)
                            .kickOff(kickoff.toInstant())
                            .venue(pairing.homeTeam().getName() + " Stadium")
                            .slug(pairing.homeTeam().getSlug() + "-vs-" + pairing.awayTeam().getSlug())
                            .wasPostponed(false)
                            .wasSuspended(false)
                            .statusHistory(new ArrayList<>())
                            .createdAt(clock.instant())
                            .updatedAt(clock.instant())
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
        return new Score(homeGoals, awayGoals);
    }

    private Map<Long, SeasonPrediction> createPredictions(
            List<User> users,
            Season season,
            List<Team> teams
    ) {
        Map<Long, SeasonPrediction> predictions = new HashMap<>();
        Contest defaultContest = contestRepo.findById(season.getMainContestId())
                .orElseThrow();

        for (User user : users) {
            // Check if prediction exists
            Optional<SeasonPrediction> existing = predictionRepo
                    .findByUserAndSeason(user.getId(), season.getId());

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
                    .createdAt(clock.instant())
                    .updatedAt(clock.instant())
                    .build();

            prediction = predictionRepo.save(prediction);
            predictions.put(user.getId(), prediction);

            // Create entry in default contest
            Entry entry = Entry.builder()
                    .userId(user.getId())
                    .contestId(defaultContest.getId())
                    .joinedAt(clock.instant())
                    .build();
            entryRepo.save(entry);

            log.info("Created prediction for user {}", user.getEmail());
        }

        return predictions;
    }

    private int seedSwaps(
            Map<Long, SeasonPrediction> predictions,
            List<Round> rounds,
            SeedingConfig config
    ) {
        int totalSwaps = 0;

        for (SeasonPrediction prediction : predictions.values()) {
            List<TeamRank> currentRankings = new ArrayList<>(prediction.getInitialRankings());
            Instant lastSwapAt = null;

            for (int roundIndex = 0; roundIndex < config.getFinishedRounds(); roundIndex++) {
                Round round = rounds.get(roundIndex);

                // Get round's first match to find kickoff date
                List<Match> roundMatches = matchRepo.findByRoundId(round.getId());
                if (roundMatches.isEmpty()) continue;

                Instant firstKickoff = roundMatches.stream()
                        .map(Match::getKickOff)
                        .min(Instant::compareTo)
                        .orElseThrow();

                // Generate 1-5 swaps
                int numSwaps = 1 + random.nextInt(5);

                for (int swapIndex = 0; swapIndex < numSwaps; swapIndex++) {
                    // Schedule swap on weekday, respecting 24h cooldown
                    Instant swapTime = generateWeekdaySwapTime(
                            firstKickoff,
                            lastSwapAt,
                            swapIndex,
                            numSwaps
                    );

                    if (swapTime.isAfter(firstKickoff)) {
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
                            String.format("%s:%d→%d", team1.getCode(),
                                    team1.getPosition(), newTeam1.getPosition()),
                            String.format("%s:%d→%d", team2.getCode(),
                                    team2.getPosition(), newTeam2.getPosition())
                    );

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

    private Instant generateWeekdaySwapTime(
            Instant roundKickoff,
            Instant lastSwapAt,
            int swapIndex,
            int totalSwaps
    ) {
        // Calculate the week before kickoff
        Instant weekBefore = roundKickoff.minus(Duration.ofDays(7));

        // Distribute swaps evenly across weekdays
        long millisPerSwap = Duration.ofDays(5).toMillis() / totalSwaps; // 5 weekdays
        Instant baseTime = weekBefore.plusMillis(swapIndex * millisPerSwap);

        // Ensure weekday
        ZonedDateTime zdt = baseTime.atZone(ZoneId.of("Europe/London"));
        while (zdt.getDayOfWeek() == DayOfWeek.SATURDAY ||
                zdt.getDayOfWeek() == DayOfWeek.SUNDAY) {
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

    private int finalizeCompletedRounds(
            Season season,
            List<Round> rounds,
            SeedingConfig config,
            List<String> warnings
    ) {
        int finalized = 0;

        for (int i = 0; i < config.getFinishedRounds(); i++) {
            Either<FinalizationError, FinalizationResult> result =
                    finalizeRoundUseCase.execute(season.getId());

            if (result.isLeft()) {
                String error = "Failed to finalize round " + (i + 1) + ": " + result.getLeft();
                warnings.add(error);
                log.warn(error);
                break;
            } else {
                finalized++;
            }
        }

        log.info("Finalized {} rounds", finalized);
        return finalized;
    }

    // Helper methods

    private List<List<MatchPairing>> generateSeasonFixtures(List<Team> teams, int totalRounds) {
        // Round-robin algorithm (same as before)
        List<List<MatchPairing>> seasonFixtures = new ArrayList<>();
        int halfSeason = totalRounds / 2;

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

    private int findTeamIndex(List<TeamRank> rankings, String teamCode) {
        for (int i = 0; i < rankings.size(); i++) {
            if (rankings.get(i).getCode().equals(teamCode)) {
                return i;
            }
        }
        return -1;
    }

    private record MatchPairing(Team homeTeam, Team awayTeam) {}
}

