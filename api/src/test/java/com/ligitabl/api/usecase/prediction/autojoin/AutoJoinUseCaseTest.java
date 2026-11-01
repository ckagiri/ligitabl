package com.ligitabl.api.usecase.prediction.autojoin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.prediction.createprediction.CreatePredictionResult;
import com.ligitabl.api.usecase.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.api.usecase.prediction.createprediction.JoinLockedRoundCommand;
import com.ligitabl.api.usecase.prediction.createprediction.RegisterDefaultTableCommand;
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.api.usecase.shared.outbox.RoundLockedPayload;
import com.ligitabl.api.usecase.shared.outbox.SeasonInPlayPayload;
import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.outbox.OutboxEvent;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoJoinUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final int MAX_ROUNDS = 3;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    UserRepo userRepo;

    @Mock
    ContestRepo contestRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    CreatePredictionUseCase createPredictionUseCase;

    @Mock
    OutboxRepo outboxRepo;

    private AutoJoinUseCase useCase;

    private final UUID seasonId = UUID.randomUUID();
    private final UUID roundId = UUID.randomUUID();
    private final Contest mainContest = Contest.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setup() {
        useCase = new AutoJoinUseCase(
                seasonRepo, userRepo, contestRepo, roundRepo, createPredictionUseCase, outboxRepo, new ObjectMapper());
    }

    private static Either<?, CreatePredictionResult> created(int atRound) {
        return Either.right(new CreatePredictionResult(UUID.randomUUID(), UUID.randomUUID(), atRound, "ok"));
    }

    private Season season(OffsetDateTime preSeasonOpensAt) {
        return Season.builder()
                .id(seasonId)
                .mainContestId(mainContest.getId())
                .currentRoundId(roundId)
                .maxRounds(MAX_ROUNDS)
                .preSeasonOpensAt(preSeasonOpensAt)
                .build();
    }

    private void givenSeason(Season season) {
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(contestRepo.findById(mainContest.getId())).thenReturn(Optional.of(mainContest));
    }

    // --- ROUND_LOCKED -------------------------------------------------------------

    private Season activeSeason() {
        return season(NOW.minusSeconds(86400).atOffset(ZoneOffset.UTC));
    }

    private RoundLockedPayload roundLocked() {
        return new RoundLockedPayload(seasonId, roundId, 1);
    }

    /** The season sits on {@code position}, which has just locked. */
    private Season givenLockedRound(int position) {
        Season season = activeSeason();
        givenSeason(season);
        when(roundRepo.findById(roundId))
                .thenReturn(Optional.of(
                        Round.builder().id(roundId).position(position).build()));
        return season;
    }

    private void givenUnjoined(UUID... userIds) {
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(List.of(userIds));
    }

    @Test
    void roundLockedAutoJoinsEligibleUsersIntoTheLockedRound() {
        // The round has locked, so a plain join would place these users at the next round. They
        // are joined as if they had submitted while it was open.
        Season season = givenLockedRound(2);
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        givenUnjoined(user1, user2);
        var command = new JoinLockedRoundCommand(season, mainContest, 2);
        when(createPredictionUseCase.execute(any(), any(JoinLockedRoundCommand.class)))
                .thenAnswer(i -> created(2));

        useCase.execute(roundLocked());

        verify(createPredictionUseCase).execute(user1, command);
        verify(createPredictionUseCase).execute(user2, command);
    }

    @Test
    void roundLockedStillJoins_onTheFinalRound() {
        Season season = givenLockedRound(MAX_ROUNDS);
        UUID user = UUID.randomUUID();
        givenUnjoined(user);
        when(createPredictionUseCase.execute(any(), any(JoinLockedRoundCommand.class)))
                .thenAnswer(i -> created(MAX_ROUNDS));

        useCase.execute(roundLocked());

        verify(createPredictionUseCase).execute(user, new JoinLockedRoundCommand(season, mainContest, MAX_ROUNDS));
    }

    @Test
    void roundLockedWithNoUnjoinedUsers_skipsResolution() {
        givenLockedRound(1);
        givenUnjoined();

        useCase.execute(roundLocked());

        verify(roundRepo, never()).findById(any());
        verify(createPredictionUseCase, never()).execute(any(), any(JoinLockedRoundCommand.class));
    }

    @Test
    void roundLockedOneUserFailure_doesNotBlockOthers() {
        givenLockedRound(1);
        UUID badUser = UUID.randomUUID();
        UUID goodUser = UUID.randomUUID();
        givenUnjoined(badUser, goodUser);
        when(createPredictionUseCase.execute(eq(badUser), any(JoinLockedRoundCommand.class)))
                .thenThrow(new RuntimeException("boom"));
        when(createPredictionUseCase.execute(eq(goodUser), any(JoinLockedRoundCommand.class)))
                .thenAnswer(i -> created(1));

        useCase.execute(roundLocked());

        verify(createPredictionUseCase).execute(eq(goodUser), any(JoinLockedRoundCommand.class));
    }

    @Test
    void roundLockedDatabaseFailure_abortsLoopImmediatelyInsteadOfBlamingTheRemainingUsers() {
        // A DataAccessException aborts the shared transaction, so the loop must stop at the real
        // culprit and let the whole batch retry.
        givenLockedRound(1);
        UUID firstUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        givenUnjoined(firstUser, laterUser);
        // jOOQ's DataAccessException, not Spring's — that is what the repos actually throw
        // here, as OutboxFailureIsolationIT demonstrates against a real Postgres.
        when(createPredictionUseCase.execute(eq(firstUser), any(JoinLockedRoundCommand.class)))
                .thenThrow(new org.jooq.exception.DataAccessException("current transaction is aborted"));

        assertThatThrownBy(() -> useCase.execute(roundLocked()))
                .isInstanceOf(org.jooq.exception.DataAccessException.class)
                .hasMessageContaining("transaction is aborted");

        verify(createPredictionUseCase, never()).execute(eq(laterUser), any(JoinLockedRoundCommand.class));
    }

    @Test
    void roundLockedSpringDatabaseFailure_alsoAbortsTheLoop() {
        // The two DataAccessException types share no supertype, so both arms are exercised.
        givenLockedRound(1);
        UUID firstUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        givenUnjoined(firstUser, laterUser);
        when(createPredictionUseCase.execute(eq(firstUser), any(JoinLockedRoundCommand.class)))
                .thenThrow(new DuplicateKeyException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> useCase.execute(roundLocked())).isInstanceOf(DuplicateKeyException.class);

        verify(createPredictionUseCase, never()).execute(eq(laterUser), any(JoinLockedRoundCommand.class));
    }

    @Test
    void roundLockedSkipsTheBatch_whenTheSeasonHasRunPastItsLastRound() {
        givenLockedRound(MAX_ROUNDS + 1);
        givenUnjoined(UUID.randomUUID());

        useCase.execute(roundLocked());

        verify(createPredictionUseCase, never()).execute(any(), any(JoinLockedRoundCommand.class));
    }

    @Test
    void roundLockedSkipsTheBatch_whenTheCurrentRoundIsMissing() {
        givenSeason(activeSeason());
        when(roundRepo.findById(roundId)).thenReturn(Optional.empty());
        givenUnjoined(UUID.randomUUID());

        useCase.execute(roundLocked());

        verify(createPredictionUseCase, never()).execute(any(), any(JoinLockedRoundCommand.class));
    }

    @Test
    void roundLockedSkipsTheBatch_whenTheMainContestIsMissing() {
        givenLockedRound(1);
        when(contestRepo.findById(mainContest.getId())).thenReturn(Optional.empty());
        givenUnjoined(UUID.randomUUID());

        useCase.execute(roundLocked());

        verify(createPredictionUseCase, never()).execute(any(), any(JoinLockedRoundCommand.class));
    }

    @Test
    void roundLockedSeasonNotFound_throws() {
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(roundLocked()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Season not found");
    }

    // --- SEASON_IN_PLAY ----------------------------------------------------------

    private Season givenInPlaySeason() {
        Season season = season(NOW.minusSeconds(30L * 86400).atOffset(ZoneOffset.UTC));
        givenSeason(season);
        return season;
    }

    private void givenEligible(UUID... userIds) {
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(List.of(userIds));
    }

    private SeasonInPlayPayload seasonInPlay() {
        return new SeasonInPlayPayload(seasonId);
    }

    private List<OutboxEvent> savedEvents() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void seasonInPlayAutoRegistersEligibleUsersWithTheDefaultTable() throws Exception {
        Season season = givenInPlaySeason();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        givenEligible(user1, user2);
        var command = new RegisterDefaultTableCommand(season, mainContest);
        when(createPredictionUseCase.execute(any(), any(RegisterDefaultTableCommand.class)))
                .thenAnswer(i -> created(0));

        useCase.execute(seasonInPlay());

        verify(createPredictionUseCase).execute(user1, command);
        verify(createPredictionUseCase).execute(user2, command);
        // Never the mid-season shape — that would cost these users their swap allowance.
        verify(createPredictionUseCase, never()).execute(any(), any(JoinLockedRoundCommand.class));
    }

    @Test
    void seasonInPlayUsesTheSeasonsPreSeasonOpensAtAsTheAnchor() throws Exception {
        Season season = givenInPlaySeason();
        givenEligible();

        useCase.execute(seasonInPlay());

        ArgumentCaptor<OffsetDateTime> anchor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(userRepo).findUnjoinedUserIdsActiveSince(eq(seasonId), anchor.capture());
        assertThat(anchor.getValue()).isEqualTo(season.getPreSeasonOpensAt());
    }

    @Test
    void seasonInPlayWritesTheWelcomeFanoutEvenWhenNobodyNeededAutoJoining() throws Exception {
        // Unlike the ROUND_LOCKED path, an empty candidate list must not return early: genuine
        // pre-season registrants still need welcoming.
        givenInPlaySeason();
        givenEligible();

        useCase.execute(seasonInPlay());

        verify(contestRepo, never()).findById(any());
        assertThat(savedEvents()).singleElement().satisfies(e -> {
            assertThat(e.getIdempotencyKey()).isEqualTo("season-welcome-fanout:" + seasonId);
            assertThat(e.getEventType()).isEqualTo(OutboxEventTypes.SEASON_WELCOME_FANOUT);
        });
    }

    @Test
    void seasonInPlayWritesExactlyOneFanoutEventNotOnePerUser() throws Exception {
        givenInPlaySeason();
        givenEligible(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(createPredictionUseCase.execute(any(), any(RegisterDefaultTableCommand.class)))
                .thenAnswer(i -> created(0));

        useCase.execute(seasonInPlay());

        assertThat(savedEvents()).hasSize(1);
    }

    @Test
    void seasonInPlayOneUserFailure_doesNotBlockOthers() throws Exception {
        givenInPlaySeason();
        UUID badUser = UUID.randomUUID();
        UUID goodUser = UUID.randomUUID();
        givenEligible(badUser, goodUser);
        when(createPredictionUseCase.execute(eq(badUser), any(RegisterDefaultTableCommand.class)))
                .thenThrow(new RuntimeException("boom"));
        when(createPredictionUseCase.execute(eq(goodUser), any(RegisterDefaultTableCommand.class)))
                .thenAnswer(i -> created(0));

        useCase.execute(seasonInPlay());

        verify(createPredictionUseCase).execute(eq(goodUser), any(RegisterDefaultTableCommand.class));
        assertThat(savedEvents()).hasSize(1);
    }

    @Test
    void seasonInPlayDatabaseFailure_abortsLoopAndWritesNoFanout() {
        givenInPlaySeason();
        UUID firstUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        givenEligible(firstUser, laterUser);
        when(createPredictionUseCase.execute(eq(firstUser), any(RegisterDefaultTableCommand.class)))
                .thenThrow(new org.jooq.exception.DataAccessException("current transaction is aborted"));

        assertThatThrownBy(() -> useCase.execute(seasonInPlay()))
                .isInstanceOf(org.jooq.exception.DataAccessException.class);

        verify(createPredictionUseCase, never()).execute(eq(laterUser), any(RegisterDefaultTableCommand.class));
        assertThat(savedEvents()).isEmpty();
    }

    @Test
    void seasonInPlayMissingMainContest_skipsBatchButStillWelcomes() throws Exception {
        givenInPlaySeason();
        when(contestRepo.findById(mainContest.getId())).thenReturn(Optional.empty());
        givenEligible(UUID.randomUUID());

        useCase.execute(seasonInPlay());

        verify(createPredictionUseCase, never()).execute(any(), any(RegisterDefaultTableCommand.class));
        assertThat(savedEvents()).hasSize(1);
    }

    @Test
    void seasonInPlayWithoutPreSeasonOpensAt_skipsAutoJoinButStillWelcomes() throws Exception {
        // Only reachable if the season was edited between enqueue and process — the enqueuer
        // guards it. Guessing a cohort would be worse than skipping one.
        givenSeason(season(null));

        useCase.execute(seasonInPlay());

        verify(userRepo, never()).findUnjoinedUserIdsActiveSince(any(), any());
        assertThat(savedEvents()).hasSize(1);
    }

    @Test
    void seasonInPlaySeasonNotFound_throwsAndWritesNoFanout() {
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(seasonInPlay()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Season not found");
        assertThat(savedEvents()).isEmpty();
    }
}
