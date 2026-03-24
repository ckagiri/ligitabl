package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TUser.T_USER;
import static com.ligitabl.model.db.tables.TUserRole.T_USER_ROLE;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.db.tables.records.UserRecord;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepo {

    private final DSLContext dsl;

    @Override
    public Optional<User> findById(UUID id) {
        var record = dsl.selectFrom(T_USER).where(T_USER.PK_ID.eq(id)).fetchOne();
        return Optional.ofNullable(map(record));
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        var record =
                dsl.selectFrom(T_USER).where(T_USER.C_EMAIL.eq(email.value())).fetchOne();
        return Optional.ofNullable(map(record));
    }

    @Override
    public Optional<User> findByPublicId(PublicId publicId) {
        var record = dsl.selectFrom(T_USER)
                .where(T_USER.C_PUBLIC_ID.eq(publicId.value()))
                .fetchOne();
        return Optional.ofNullable(map(record));
    }

    @Override
    public Optional<User> findByGoogleId(String googleId) {
        if (googleId == null || googleId.isBlank()) {
            return Optional.empty();
        }

        var record = dsl.selectFrom(T_USER)
                .where(T_USER.C_GOOGLE_SUBJECT.eq(googleId))
                .fetchOne();

        return Optional.ofNullable(map(record));
    }

    @Override
    public User create(User model) {
        if (model.getId() == null) {
            throw new IllegalArgumentException("User.id must not be null on create");
        }

        if (model.getPublicId() == null) {
            throw new IllegalArgumentException("User.publicId must not be null on create");
        }

        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);

            UserRecord rec = tx.newRecord(T_USER);
            rec.setId(model.getId());
            rec.setPublicId(model.getPublicId().value());
            rec.setEmail(model.getEmail().value());
            rec.setPasswordHash(
                    model.getPassword() == null ? null : model.getPassword().value());
            rec.setDisplayName(model.getDisplayName());
            rec.setEmailVerified(model.isEmailVerified());
            rec.setGoogleSubject(model.getGoogleId());
            rec.store();
            rec.refresh();

            for (Role role : model.getRoles()) {
                tx.insertInto(T_USER_ROLE)
                        .set(T_USER_ROLE.FK_USER_ID, model.getId())
                        .set(T_USER_ROLE.C_ROLE, role.getValue())
                        .onConflictDoNothing()
                        .execute();
            }
        });

        return model;
    }

    @Override
    public void update(User user) {
        dsl.update(T_USER)
                .set(T_USER.C_DISPLAY_NAME, user.getDisplayName())
                .set(T_USER.C_EMAIL_VERIFIED, user.isEmailVerified())
                .set(T_USER.C_GOOGLE_SUBJECT, user.getGoogleId())
                .where(T_USER.PK_ID.eq(user.getId()))
                .execute();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return dsl.fetchExists(dsl.selectOne().from(T_USER).where(T_USER.C_EMAIL.eq(email.value())));
    }

    @Override
    public void updatePassword(UUID userId, Password.Hashed password) {
        dsl.update(T_USER)
                .set(T_USER.C_PASSWORD_HASH, password.value())
                .where(T_USER.PK_ID.eq(userId))
                .execute();
    }

    private User map(UserRecord record) {
        if (record == null) {
            return null;
        }

        UUID id = record.getId();
        Set<Role> roles = Set.of();
        if (id != null) {
            roles = dsl.select(T_USER_ROLE.C_ROLE)
                    .from(T_USER_ROLE)
                    .where(T_USER_ROLE.FK_USER_ID.eq(id))
                    .fetchSet(r -> Role.fromString(r.get(T_USER_ROLE.C_ROLE)));
        }

        return User.builder()
                .id(id)
                .publicId(record.getPublicId() == null ? null : PublicId.create(record.getPublicId()))
                .email(record.getEmail() == null ? null : Email.create(record.getEmail()))
                .password(record.getPasswordHash() == null ? null : Password.Hashed.of(record.getPasswordHash()))
                .displayName(record.getDisplayName())
                .roles(roles)
                .emailVerified(Boolean.TRUE.equals(record.getEmailVerified()))
                .googleId(record.getGoogleSubject())
                .build();
    }
}
