package com.ligitabl.model.repo;

import static com.ligitabl.model.db.tables.TUser.T_USER;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.EmailVerificationToken;
import com.ligitabl.model.infra.EmailVerificationTokenPersistenceAdapter;
import com.ligitabl.model.infra.UserPersistenceAdapter;

@Tag("integration")
class EmailVerificationTokenRepoTest {

    /**
     * The instant every token here is minted from and evaluated at.
     *
     * <p>Frozen for the run rather than read per call: this class exercises persistence, not expiry,
     * so what matters is that one instant is used consistently — a token created from one
     * {@code Instant.now()} and validated against a later one is a validity window nobody chose, and
     * a `deleteExpired` fixture built the same way is the flaky kind.
     *
     * <p>Real time rather than a fixed date, because {@code deleteExpired} compares against the
     * database's own {@code now()}. {@code api}'s {@code TestClock} makes the same trade for the same
     * reason; this module cannot import it.
     */
    private static final Instant NOW = Instant.now();

    private static Connection jdbc;
    private static DSLContext dsl;
    private static EmailVerificationTokenRepo repo;
    private static UserRepo userRepo;

    @BeforeAll
    static void setup() throws Exception {
        jdbc = TestDbConnections.open();
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new EmailVerificationTokenPersistenceAdapter(dsl);
        userRepo = new UserPersistenceAdapter(dsl);

        TestDbCleaner.truncatePublicTables(dsl);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jdbc != null) {
            jdbc.close();
        }
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        dsl.insertInto(T_USER)
                .set(T_USER.PK_ID, userId)
                .set(T_USER.C_EMAIL, "user-" + userId + "@example.com")
                .execute();
        return userId;
    }

    @Test
    void saveAndFindByToken_roundTrips() {
        UUID userId = insertUser();
        EmailVerificationToken token = EmailVerificationToken.create(userId, 48, NOW);

        repo.save(token);

        Optional<EmailVerificationToken> found = repo.findByToken(token.getToken());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().isUsed()).isFalse();
        assertThat(found.get().getUsedAt()).isNull();
        assertThat(found.get().getExpiresAt()).isCloseTo(token.getExpiresAt(), within(1));
        assertThat(found.get().isValid(NOW)).isTrue();
    }

    @Test
    void findLatestForUser_returnsNewestByCreatedAt() {
        UUID userId = insertUser();
        repo.save(tokenAt(userId, NOW.minus(2, ChronoUnit.HOURS)));
        EmailVerificationToken newest = tokenAt(userId, NOW);
        repo.save(newest);
        repo.save(tokenAt(userId, NOW.minus(1, ChronoUnit.HOURS)));

        Optional<EmailVerificationToken> latest = repo.findLatestForUser(userId);
        assertThat(latest).isPresent();
        assertThat(latest.get().getToken()).isEqualTo(newest.getToken());
    }

    @Test
    void findLatestForUser_emptyWhenNoTokens() {
        UUID userId = insertUser();
        assertThat(repo.findLatestForUser(userId)).isEmpty();
    }

    @Test
    void invalidateAllForUser_marksUnusedTokensUsed() {
        UUID userId = insertUser();
        EmailVerificationToken token = EmailVerificationToken.create(userId, 48, NOW);
        repo.save(token);

        repo.invalidateAllForUser(userId);

        Optional<EmailVerificationToken> found = repo.findByToken(token.getToken());
        assertThat(found).isPresent();
        assertThat(found.get().isUsed()).isTrue();
        assertThat(found.get().getUsedAt()).isNotNull();
        assertThat(found.get().isValid(NOW)).isFalse();
    }

    @Test
    void update_persistsMarkAsUsed() {
        UUID userId = insertUser();
        EmailVerificationToken token = EmailVerificationToken.create(userId, 48, NOW);
        repo.save(token);

        repo.update(token.markAsUsed(NOW));

        Optional<EmailVerificationToken> found = repo.findByToken(token.getToken());
        assertThat(found).isPresent();
        assertThat(found.get().isUsed()).isTrue();
        assertThat(found.get().getUsedAt()).isNotNull();
    }

    @Test
    void deleteExpired_removesOnlyExpiredTokens() {
        UUID userId = insertUser();
        Instant past = NOW.minus(3, ChronoUnit.DAYS);
        EmailVerificationToken expired = EmailVerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .createdAt(past)
                .expiresAt(past.plus(1, ChronoUnit.HOURS))
                .used(false)
                .usedAt(null)
                .build();
        EmailVerificationToken live = EmailVerificationToken.create(userId, 48, NOW);
        repo.save(expired);
        repo.save(live);

        int deleted = repo.deleteExpired();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(repo.findByToken(expired.getToken())).isEmpty();
        assertThat(repo.findByToken(live.getToken())).isPresent();
    }

    @Test
    void deleteAllForUser_removesAllTokens() {
        UUID userId = insertUser();
        repo.save(EmailVerificationToken.create(userId, 48, NOW));
        repo.save(EmailVerificationToken.create(userId, 48, NOW));

        repo.deleteAllForUser(userId);

        assertThat(repo.findLatestForUser(userId)).isEmpty();
    }

    @Test
    void markEmailVerified_setsFlagAndTimestamp() {
        UUID userId = insertUser();
        OffsetDateTime verifiedAt = OffsetDateTime.now();

        var before = userRepo.findById(userId).orElseThrow();
        assertThat(before.isEmailVerified()).isFalse();
        assertThat(before.getEmailVerifiedAt()).isNull();

        userRepo.markEmailVerified(userId, verifiedAt);

        var after = userRepo.findById(userId).orElseThrow();
        assertThat(after.isEmailVerified()).isTrue();
        assertThat(after.getEmailVerifiedAt()).isNotNull();
        assertThat(after.getEmailVerifiedAt().toInstant()).isCloseTo(verifiedAt.toInstant(), within(1));
    }

    @Test
    void userInsert_defaultsToUnverified() {
        UUID userId = insertUser();

        var user = userRepo.findById(userId).orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getEmailVerifiedAt()).isNull();
    }

    private static org.assertj.core.data.TemporalUnitOffset within(int seconds) {
        return org.assertj.core.api.Assertions.within((long) seconds, ChronoUnit.SECONDS);
    }

    private static EmailVerificationToken tokenAt(UUID userId, Instant createdAt) {
        return EmailVerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .createdAt(createdAt)
                .expiresAt(createdAt.plus(48, ChronoUnit.HOURS))
                .used(false)
                .usedAt(null)
                .build();
    }
}
