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
import com.ligitabl.api.usecase.prediction.createprediction.CreatePredictionCommand;
import com.ligitabl.api.usecase.prediction.createprediction.CreatePredictionError;
import com.ligitabl.api.usecase.prediction.createprediction.CreatePredictionResult;
import com.ligitabl.api.usecase.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.api.usecase.shared.outbox.RoundLockedPayload;
import com.ligitabl.api.usecase.shared.outbox.SeasonInPlayPayload;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoJoinUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    UserRepo userRepo;

    @Mock
    CreatePredictionUseCase createPredictionUseCase;

    @Mock
    OutboxRepo outboxRepo;

    private AutoJoinUseCase useCase;

    private final UUID seasonId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        useCase = new AutoJoinUseCase(seasonRepo, userRepo, createPredictionUseCase, outboxRepo, new ObjectMapper());
    }

    private static CreatePredictionResult created(int atRound) {
        return new CreatePredictionResult(UUID.randomUUID(), UUID.randomUUID(), atRound, "ok");
    }

    // --- ROUND_LOCKED -------------------------------------------------------------

    private Season activeSeason() {
        return Season.builder()
                .id(seasonId)
                .mainContestId(UUID.randomUUID())
                .preSeasonOpensAt(NOW.minusSeconds(86400).atOffset(ZoneOffset.UTC))
                .build();
    }

    private RoundLockedPayload roundLocked() {
        return new RoundLockedPayload(seasonId, UUID.randomUUID(), 1);
    }

    private CreatePredictionUseCase.JoinCtx joinCtx(Season season) {
        return new CreatePredictionUseCase.JoinCtx(
                season, Contest.builder().id(UUID.randomUUID()).build(), 1, 1);
    }

    @Test
    void roundLockedAutoJoinsEligibleUsers() {
        Season season = activeSeason();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        var ctx = joinCtx(season);

        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(List.of(user1, user2));
        when(createPredictionUseCase.resolveJoinContextAsOpen(season)).thenReturn(Either.right(ctx));
        when(createPredictionUseCase.executeWithContext(any(), eq(ctx), any())).thenReturn(Either.right(created(1)));

        useCase.execute(roundLocked());

        verify(createPredictionUseCase).executeWithContext(eq(user1), eq(ctx), any(CreatePredictionCommand.class));
        verify(createPredictionUseCase).executeWithContext(eq(user2), eq(ctx), any(CreatePredictionCommand.class));
    }

    @Test
    void roundLockedWithNoUnjoinedUsers_skipsContextResolution() {
        Season season = activeSeason();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(List.of());

        useCase.execute(roundLocked());

        verify(createPredictionUseCase, never()).resolveJoinContextAsOpen(any());
    }

    @Test
    void roundLockedOneUserFailure_doesNotBlockOthers() {
        Season season = activeSeason();
        UUID badUser = UUID.randomUUID();
        UUID goodUser = UUID.randomUUID();
        var ctx = joinCtx(season);

        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(List.of(badUser, goodUser));
        when(createPredictionUseCase.resolveJoinContextAsOpen(season)).thenReturn(Either.right(ctx));
        when(createPredictionUseCase.executeWithContext(eq(badUser), eq(ctx), any()))
                .thenThrow(new RuntimeException("boom"));
        when(createPredictionUseCase.executeWithContext(eq(goodUser), eq(ctx), any()))
                .thenReturn(Either.right(created(1)));

        useCase.execute(roundLocked());

        verify(createPredictionUseCase).executeWithContext(eq(goodUser), eq(ctx), any());
    }

    @Test
    void roundLockedDatabaseFailure_abortsLoopImmediatelyInsteadOfBlamingTheRemainingUsers() {
        // A DataAccessException aborts the shared transaction, so every later user would fail
        // too and be logged as if at fault. The loop must stop at the real culprit and let the
        // whole batch retry.
        Season season = activeSeason();
        UUID firstUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        var ctx = joinCtx(season);

        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(List.of(firstUser, laterUser));
        when(createPredictionUseCase.resolveJoinContextAsOpen(season)).thenReturn(Either.right(ctx));
        // jOOQ's DataAccessException, not Spring's — that is what the repos actually throw
        // here, as OutboxFailureIsolationIT demonstrates against a real Postgres.
        when(createPredictionUseCase.executeWithContext(eq(firstUser), eq(ctx), any()))
                .thenThrow(new org.jooq.exception.DataAccessException("current transaction is aborted"));

        assertThatThrownBy(() -> useCase.execute(roundLocked()))
                .isInstanceOf(org.jooq.exception.DataAccessException.class)
                .hasMessageContaining("transaction is aborted");

        verify(createPredictionUseCase, never()).executeWithContext(eq(laterUser), any(), any());
    }

    @Test
    void roundLockedSpringDatabaseFailure_alsoAbortsTheLoop() {
        // The two DataAccessException types share no supertype, so both arms are exercised.
        Season season = activeSeason();
        UUID firstUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        var ctx = joinCtx(season);

        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(List.of(firstUser, laterUser));
        when(createPredictionUseCase.resolveJoinContextAsOpen(season)).thenReturn(Either.right(ctx));
        when(createPredictionUseCase.executeWithContext(eq(firstUser), eq(ctx), any()))
                .thenThrow(new DuplicateKeyException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> useCase.execute(roundLocked())).isInstanceOf(DuplicateKeyException.class);

        verify(createPredictionUseCase, never()).executeWithContext(eq(laterUser), any(), any());
    }

    @Test
    void roundLockedContextResolutionFails_skipsBatch() {
        Season season = activeSeason();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(List.of(UUID.randomUUID()));
        when(createPredictionUseCase.resolveJoinContextAsOpen(season))
                .thenReturn(Either.left(new CreatePredictionError.CurrentRoundNotFound(seasonId)));

        useCase.execute(roundLocked());

        verify(createPredictionUseCase, never()).executeWithContext(any(), any(), any());
    }

    @Test
    void roundLockedSeasonNotFound_throws() {
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(roundLocked()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Season not found");
    }

    // --- SEASON_IN_PLAY ----------------------------------------------------------

    private Season inPlaySeason() {
        return Season.builder()
                .id(seasonId)
                .mainContestId(UUID.randomUUID())
                .preSeasonOpensAt(NOW.minusSeconds(30L * 86400).atOffset(ZoneOffset.UTC))
                .build();
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
    void seasonInPlayAutoRegistersEligibleUsersAsRoundZero() throws Exception {
        Season season = inPlaySeason();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        Contest mainContest = Contest.builder().id(UUID.randomUUID()).build();

        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(List.of(user1, user2));
        when(createPredictionUseCase.resolveMainContest(season)).thenReturn(Either.right(mainContest));
        when(createPredictionUseCase.autoRegisterDefaultTable(any(), eq(season), eq(mainContest)))
                .thenReturn(Either.right(created(0)));

        useCase.execute(seasonInPlay());

        verify(createPredictionUseCase).autoRegisterDefaultTable(eq(user1), eq(season), eq(mainContest));
        verify(createPredictionUseCase).autoRegisterDefaultTable(eq(user2), eq(season), eq(mainContest));
        // Never the mid-season shape — that would cost these users their swap allowance.
        verify(createPredictionUseCase, never()).executeWithContext(any(), any(), any());
    }

    @Test
    void seasonInPlayUsesTheSeasonsPreSeasonOpensAtAsTheAnchor() throws Exception {
        Season season = inPlaySeason();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(List.of());

        useCase.execute(seasonInPlay());

        ArgumentCaptor<OffsetDateTime> anchor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(userRepo).findUnjoinedUserIdsActiveSince(eq(seasonId), anchor.capture());
        assertThat(anchor.getValue()).isEqualTo(season.getPreSeasonOpensAt());
    }

    @Test
    void seasonInPlayWritesTheWelcomeFanoutEvenWhenNobodyNeededAutoJoining() throws Exception {
        // The trap: the ROUND_LOCKED path returns early on an empty candidate list. Doing that
        // here would silently skip welcoming genuine pre-season registrants, who exist regardless
        // of whether anyone needed auto-joining.
        Season season = inPlaySeason();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(List.of());

        useCase.execute(seasonInPlay());

        verify(createPredictionUseCase, never()).resolveMainContest(any());
        assertThat(savedEvents()).singleElement().satisfies(e -> {
            assertThat(e.getIdempotencyKey()).isEqualTo("season-welcome-fanout:" + seasonId);
            assertThat(e.getEventType()).isEqualTo(OutboxEventTypes.SEASON_WELCOME_FANOUT);
        });
    }

    @Test
    void seasonInPlayWritesExactlyOneFanoutEventNotOnePerUser() throws Exception {
        Season season = inPlaySeason();
        Contest mainContest = Contest.builder().id(UUID.randomUUID()).build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any()))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        when(createPredictionUseCase.resolveMainContest(season)).thenReturn(Either.right(mainContest));
        when(createPredictionUseCase.autoRegisterDefaultTable(any(), any(), any()))
                .thenReturn(Either.right(created(0)));

        useCase.execute(seasonInPlay());

        assertThat(savedEvents()).hasSize(1);
    }

    @Test
    void seasonInPlayOneUserFailure_doesNotBlockOthers() throws Exception {
        Season season = inPlaySeason();
        UUID badUser = UUID.randomUUID();
        UUID goodUser = UUID.randomUUID();
        Contest mainContest = Contest.builder().id(UUID.randomUUID()).build();

        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(List.of(badUser, goodUser));
        when(createPredictionUseCase.resolveMainContest(season)).thenReturn(Either.right(mainContest));
        when(createPredictionUseCase.autoRegisterDefaultTable(eq(badUser), any(), any()))
                .thenThrow(new RuntimeException("boom"));
        when(createPredictionUseCase.autoRegisterDefaultTable(eq(goodUser), any(), any()))
                .thenReturn(Either.right(created(0)));

        useCase.execute(seasonInPlay());

        verify(createPredictionUseCase).autoRegisterDefaultTable(eq(goodUser), any(), any());
        assertThat(savedEvents()).hasSize(1);
    }

    @Test
    void seasonInPlayDatabaseFailure_abortsLoopAndWritesNoFanout() {
        Season season = inPlaySeason();
        UUID firstUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        Contest mainContest = Contest.builder().id(UUID.randomUUID()).build();

        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(List.of(firstUser, laterUser));
        when(createPredictionUseCase.resolveMainContest(season)).thenReturn(Either.right(mainContest));
        when(createPredictionUseCase.autoRegisterDefaultTable(eq(firstUser), any(), any()))
                .thenThrow(new org.jooq.exception.DataAccessException("current transaction is aborted"));

        assertThatThrownBy(() -> useCase.execute(seasonInPlay()))
                .isInstanceOf(org.jooq.exception.DataAccessException.class);

        verify(createPredictionUseCase, never()).autoRegisterDefaultTable(eq(laterUser), any(), any());
        assertThat(savedEvents()).isEmpty();
    }

    @Test
    void seasonInPlayContestResolutionFails_skipsBatchButStillWelcomes() throws Exception {
        Season season = inPlaySeason();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(List.of(UUID.randomUUID()));
        when(createPredictionUseCase.resolveMainContest(season))
                .thenReturn(Either.left(new CreatePredictionError.MainContestNotFound()));

        useCase.execute(seasonInPlay());

        verify(createPredictionUseCase, never()).autoRegisterDefaultTable(any(), any(), any());
        assertThat(savedEvents()).hasSize(1);
    }

    @Test
    void seasonInPlayWithoutPreSeasonOpensAt_skipsAutoJoinButStillWelcomes() throws Exception {
        // Only reachable if the season was edited between enqueue and process — the enqueuer
        // guards it. Guessing a cohort would be worse than skipping one.
        Season season = Season.builder()
                .id(seasonId)
                .mainContestId(UUID.randomUUID())
                .preSeasonOpensAt(null)
                .build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));

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
