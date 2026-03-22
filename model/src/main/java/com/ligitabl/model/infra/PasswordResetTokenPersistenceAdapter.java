package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TPasswordResetToken.T_PASSWORD_RESET_TOKEN;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.jooq.DSLContext;

import com.ligitabl.model.db.tables.records.PasswordResetTokenRecord;
import com.ligitabl.model.domain.PasswordResetToken;
import com.ligitabl.model.repo.PasswordResetTokenRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PasswordResetTokenPersistenceAdapter implements PasswordResetTokenRepo {
    private final DSLContext dsl;

    @Override
    public void save(PasswordResetToken token) {
        dsl.insertInto(T_PASSWORD_RESET_TOKEN)
                .set(T_PASSWORD_RESET_TOKEN.PK_TOKEN, token.getToken())
                .set(T_PASSWORD_RESET_TOKEN.FK_USER_ID, token.getUserId())
                .set(T_PASSWORD_RESET_TOKEN.C_EXPIRES_AT, toOffset(token.getExpiresAt()))
                .set(T_PASSWORD_RESET_TOKEN.C_USED, token.isUsed())
                .set(T_PASSWORD_RESET_TOKEN.C_USED_AT, toOffset(token.getUsedAt()))
                .set(T_PASSWORD_RESET_TOKEN.C_CREATED_AT, toOffset(token.getCreatedAt()))
                .execute();
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return dsl.selectFrom(T_PASSWORD_RESET_TOKEN)
                .where(T_PASSWORD_RESET_TOKEN.PK_TOKEN.eq(token))
                .fetchOptional(this::map);
    }

    @Override
    public void invalidateAllForUser(java.util.UUID userId) {
        dsl.update(T_PASSWORD_RESET_TOKEN)
                .set(T_PASSWORD_RESET_TOKEN.C_USED, true)
                .set(T_PASSWORD_RESET_TOKEN.C_USED_AT, OffsetDateTime.now())
                .where(T_PASSWORD_RESET_TOKEN.FK_USER_ID.eq(userId))
                .and(T_PASSWORD_RESET_TOKEN.C_USED.eq(false))
                .execute();
    }

    @Override
    public void update(PasswordResetToken token) {
        dsl.update(T_PASSWORD_RESET_TOKEN)
                .set(T_PASSWORD_RESET_TOKEN.C_USED, token.isUsed())
                .set(T_PASSWORD_RESET_TOKEN.C_USED_AT, toOffset(token.getUsedAt()))
                .where(T_PASSWORD_RESET_TOKEN.PK_TOKEN.eq(token.getToken()))
                .execute();
    }

    @Override
    public int deleteExpired() {
        return dsl.deleteFrom(T_PASSWORD_RESET_TOKEN)
                .where(T_PASSWORD_RESET_TOKEN.C_EXPIRES_AT.lt(OffsetDateTime.now()))
                .execute();
    }

    private PasswordResetToken map(PasswordResetTokenRecord record) {
        OffsetDateTime expiresAt = record.getExpiresAt();
        OffsetDateTime usedAt = record.getUsedAt();
        OffsetDateTime createdAt = record.getCreatedAt();

        return PasswordResetToken.builder()
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
