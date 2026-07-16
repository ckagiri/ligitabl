package com.ligitabl.model.repo;

import static com.ligitabl.model.db.tables.TUser.T_USER;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
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

    private static Connection jdbc;
    private static DSLContext dsl;
    private static EmailVerificationTokenRepo repo;
    private static UserRepo userRepo;

    @BeforeAll
    static void setup() throws Exception {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "55433");
        String db = System.getenv().getOrDefault("DB_NAME", "ligitabl_test");
        String user = System.getenv().getOrDefault("DB_USER", "ligitabl");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "ligitabl");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
        jdbc = DriverManager.getConnection(url, user, password);
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
        EmailVerificationToken token = EmailVerificationToken.create(userId, 48);

        repo.save(token);

        Optional<EmailVerificationToken> found = repo.findByToken(token.getToken());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().isUsed()).isFalse();
        assertThat(found.get().getUsedAt()).isNull();
        assertThat(found.get().getExpiresAt()).isCloseTo(token.getExpiresAt(), within(1));
        assertThat(found.get().isValid()).isTrue();
    }

    @Test
    void findLatestForUser_returnsNewestByCreatedAt() {
        UUID userId = insertUser();
        Instant now = Instant.now();

        repo.save(tokenAt(userId, now.minus(2, ChronoUnit.HOURS)));
        EmailVerificationToken newest = tokenAt(userId, now);
        repo.save(newest);
        repo.save(tokenAt(userId, now.minus(1, ChronoUnit.HOURS)));

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
        EmailVerificationToken token = EmailVerificationToken.create(userId, 48);
        repo.save(token);

        repo.invalidateAllForUser(userId);

        Optional<EmailVerificationToken> found = repo.findByToken(token.getToken());
        assertThat(found).isPresent();
        assertThat(found.get().isUsed()).isTrue();
        assertThat(found.get().getUsedAt()).isNotNull();
        assertThat(found.get().isValid()).isFalse();
    }

    @Test
    void update_persistsMarkAsUsed() {
        UUID userId = insertUser();
        EmailVerificationToken token = EmailVerificationToken.create(userId, 48);
        repo.save(token);

        repo.update(token.markAsUsed());

        Optional<EmailVerificationToken> found = repo.findByToken(token.getToken());
        assertThat(found).isPresent();
        assertThat(found.get().isUsed()).isTrue();
        assertThat(found.get().getUsedAt()).isNotNull();
    }

    @Test
    void deleteExpired_removesOnlyExpiredTokens() {
        UUID userId = insertUser();
        Instant past = Instant.now().minus(3, ChronoUnit.DAYS);
        EmailVerificationToken expired = EmailVerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .createdAt(past)
                .expiresAt(past.plus(1, ChronoUnit.HOURS))
                .used(false)
                .usedAt(null)
                .build();
        EmailVerificationToken live = EmailVerificationToken.create(userId, 48);
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
        repo.save(EmailVerificationToken.create(userId, 48));
        repo.save(EmailVerificationToken.create(userId, 48));

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
