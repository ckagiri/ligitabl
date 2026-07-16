package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TEmailVerificationToken.T_EMAIL_VERIFICATION_TOKEN;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;

import com.ligitabl.model.db.tables.records.EmailVerificationTokenRecord;
import com.ligitabl.model.domain.EmailVerificationToken;
import com.ligitabl.model.repo.EmailVerificationTokenRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EmailVerificationTokenPersistenceAdapter implements EmailVerificationTokenRepo {
    private final DSLContext dsl;

    @Override
    public void save(EmailVerificationToken token) {
        dsl.insertInto(T_EMAIL_VERIFICATION_TOKEN)
                .set(T_EMAIL_VERIFICATION_TOKEN.PK_TOKEN, token.getToken())
                .set(T_EMAIL_VERIFICATION_TOKEN.FK_USER_ID, token.getUserId())
                .set(T_EMAIL_VERIFICATION_TOKEN.C_EXPIRES_AT, toOffset(token.getExpiresAt()))
                .set(T_EMAIL_VERIFICATION_TOKEN.C_USED, token.isUsed())
                .set(T_EMAIL_VERIFICATION_TOKEN.C_USED_AT, toOffset(token.getUsedAt()))
                .set(T_EMAIL_VERIFICATION_TOKEN.C_CREATED_AT, toOffset(token.getCreatedAt()))
                .execute();
    }

    @Override
    public Optional<EmailVerificationToken> findByToken(String token) {
        return dsl.selectFrom(T_EMAIL_VERIFICATION_TOKEN)
                .where(T_EMAIL_VERIFICATION_TOKEN.PK_TOKEN.eq(token))
                .fetchOptional(this::map);
    }

    @Override
    public Optional<EmailVerificationToken> findLatestForUser(UUID userId) {
        return dsl.selectFrom(T_EMAIL_VERIFICATION_TOKEN)
                .where(T_EMAIL_VERIFICATION_TOKEN.FK_USER_ID.eq(userId))
                .orderBy(T_EMAIL_VERIFICATION_TOKEN.C_CREATED_AT.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    @Override
    public void invalidateAllForUser(UUID userId) {
        dsl.update(T_EMAIL_VERIFICATION_TOKEN)
                .set(T_EMAIL_VERIFICATION_TOKEN.C_USED, true)
                .set(T_EMAIL_VERIFICATION_TOKEN.C_USED_AT, OffsetDateTime.now())
                .where(T_EMAIL_VERIFICATION_TOKEN.FK_USER_ID.eq(userId))
                .and(T_EMAIL_VERIFICATION_TOKEN.C_USED.eq(false))
                .execute();
    }

    @Override
    public void update(EmailVerificationToken token) {
        dsl.update(T_EMAIL_VERIFICATION_TOKEN)
                .set(T_EMAIL_VERIFICATION_TOKEN.C_USED, token.isUsed())
                .set(T_EMAIL_VERIFICATION_TOKEN.C_USED_AT, toOffset(token.getUsedAt()))
                .where(T_EMAIL_VERIFICATION_TOKEN.PK_TOKEN.eq(token.getToken()))
                .execute();
    }

    @Override
    public int deleteExpired() {
        return dsl.deleteFrom(T_EMAIL_VERIFICATION_TOKEN)
                .where(T_EMAIL_VERIFICATION_TOKEN.C_EXPIRES_AT.lt(OffsetDateTime.now()))
                .execute();
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        dsl.deleteFrom(T_EMAIL_VERIFICATION_TOKEN)
                .where(T_EMAIL_VERIFICATION_TOKEN.FK_USER_ID.eq(userId))
                .execute();
    }

    private EmailVerificationToken map(EmailVerificationTokenRecord record) {
        OffsetDateTime expiresAt = record.getExpiresAt();
        OffsetDateTime usedAt = record.getUsedAt();
        OffsetDateTime createdAt = record.getCreatedAt();

        return EmailVerificationToken.builder()
                .token(record.getToken())
                .userId(record.getUserId())
                .expiresAt(expiresAt == null ? null : expiresAt.toInstant())
                .used(Boolean.TRUE.equals(record.getUsed()))
                .usedAt(usedAt == null ? null : usedAt.toInstant())
                .createdAt(createdAt == null ? null : createdAt.toInstant())
                .build();
    }

    private OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }
}
