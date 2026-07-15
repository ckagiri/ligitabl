package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TUser.T_USER;
import static com.ligitabl.model.db.tables.TUserRole.T_USER_ROLE;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        var record =
                dsl.selectFrom(T_USER).where(T_USER.C_USERNAME.eq(username)).fetchOne();

        return Optional.ofNullable(map(record));
    }

    @Override
    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return dsl.fetchExists(dsl.selectOne().from(T_USER).where(T_USER.C_USERNAME.eq(username)));
    }

    @Override
    public void updateUsername(UUID userId, String username) {
        dsl.update(T_USER)
                .set(T_USER.C_USERNAME, username)
                .where(T_USER.PK_ID.eq(userId))
                .execute();
    }

    @Override
    public User create(User model) {
        if (model.getId() == null) {
            throw new IllegalArgumentException("User.id must not be null on create");
        }

        if (model.getPublicId() == null) {
            throw new IllegalArgumentException("User.publicId must not be null on create");
        }

        final UserRecord[] refreshed = new UserRecord[1];

        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);

            UserRecord rec = tx.newRecord(T_USER);
            rec.setId(model.getId());
            rec.setPublicId(model.getPublicId().value());
            rec.setEmail(model.getEmail().value());
            rec.setPasswordHash(
                    model.getPassword() == null ? null : model.getPassword().value());
            rec.setUsername(model.getUsername());
            rec.setDisplayName(model.getDisplayName());
            rec.setEmailVerified(model.isEmailVerified());
            rec.setGoogleSubject(model.getGoogleId());
            rec.store();
            rec.refresh();
            refreshed[0] = rec;

            for (Role role : model.getRoles()) {
                tx.insertInto(T_USER_ROLE)
                        .set(T_USER_ROLE.FK_USER_ID, model.getId())
                        .set(T_USER_ROLE.C_ROLE, role.getValue())
                        .onConflictDoNothing()
                        .execute();
            }
        });

        UserRecord rec = refreshed[0];
        return User.builder()
                .id(rec.getId())
                .publicId(rec.getPublicId() == null ? null : PublicId.create(rec.getPublicId()))
                .email(rec.getEmail() == null ? null : Email.create(rec.getEmail()))
                .password(rec.getPasswordHash() == null ? null : Password.Hashed.of(rec.getPasswordHash()))
                .username(rec.getUsername())
                .displayName(rec.getDisplayName())
                .emailVerified(Boolean.TRUE.equals(rec.getEmailVerified()))
                .googleId(rec.getGoogleSubject())
                .createDate(rec.getCreateDate())
                .updateDate(rec.getUpdateDate())
                .lastLoginAt(rec.getLastLoginAt())
                .roles(model.getRoles())
                .build();
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

    @Override
    public Map<UUID, User> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();

        var records = dsl.selectFrom(T_USER).where(T_USER.PK_ID.in(ids)).fetch();

        if (records.isEmpty()) return Map.of();

        Set<UUID> foundIds = new HashSet<>();
        records.forEach(r -> foundIds.add(r.getId()));

        Map<UUID, Set<Role>> rolesByUserId = new LinkedHashMap<>();
        dsl.select(T_USER_ROLE.FK_USER_ID, T_USER_ROLE.C_ROLE)
                .from(T_USER_ROLE)
                .where(T_USER_ROLE.FK_USER_ID.in(foundIds))
                .forEach(r -> rolesByUserId
                        .computeIfAbsent(r.get(T_USER_ROLE.FK_USER_ID), k -> new HashSet<>())
                        .add(Role.fromString(r.get(T_USER_ROLE.C_ROLE))));

        Map<UUID, User> result = new LinkedHashMap<>();
        for (var rec : records) {
            UUID id = rec.getId();
            result.put(id, map(rec, rolesByUserId.getOrDefault(id, Set.of())));
        }
        return result;
    }

    @Override
    public List<User> findAllPaged(int offset, int limit) {
        var records = dsl.selectFrom(T_USER)
                .orderBy(
                        T_USER.C_LAST_LOGIN_AT.desc().nullsLast(),
                        T_USER.C_UPDATE_DATE.desc().nullsLast())
                .limit(limit)
                .offset(offset)
                .fetch();

        if (records.isEmpty()) return List.of();

        Set<UUID> ids = new HashSet<>();
        records.forEach(r -> ids.add(r.getId()));

        Map<UUID, Set<Role>> rolesByUserId = new LinkedHashMap<>();
        dsl.select(T_USER_ROLE.FK_USER_ID, T_USER_ROLE.C_ROLE)
                .from(T_USER_ROLE)
                .where(T_USER_ROLE.FK_USER_ID.in(ids))
                .forEach(r -> rolesByUserId
                        .computeIfAbsent(r.get(T_USER_ROLE.FK_USER_ID), k -> new HashSet<>())
                        .add(Role.fromString(r.get(T_USER_ROLE.C_ROLE))));

        List<User> result = new ArrayList<>();
        for (var rec : records) {
            result.add(map(rec, rolesByUserId.getOrDefault(rec.getId(), Set.of())));
        }
        return result;
    }

    @Override
    public long countAll() {
        return dsl.fetchCount(T_USER);
    }

    @Override
    public void updateLastLoginAt(UUID userId, OffsetDateTime at) {
        dsl.update(T_USER)
                .set(T_USER.C_LAST_LOGIN_AT, at)
                .where(T_USER.PK_ID.eq(userId))
                .execute();
    }

    @Override
    public void delete(UUID userId) {
        dsl.deleteFrom(T_USER).where(T_USER.PK_ID.eq(userId)).execute();
    }

    @Override
    public Map<UUID, String> findDisplayNamesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return dsl.select(T_USER.PK_ID, T_USER.C_DISPLAY_NAME)
                .from(T_USER)
                .where(T_USER.PK_ID.in(ids))
                .fetchMap(T_USER.PK_ID, T_USER.C_DISPLAY_NAME);
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

        return map(record, roles);
    }

    private User map(UserRecord record, Set<Role> roles) {
        return User.builder()
                .id(record.getId())
                .publicId(record.getPublicId() == null ? null : PublicId.create(record.getPublicId()))
                .email(record.getEmail() == null ? null : Email.create(record.getEmail()))
                .password(record.getPasswordHash() == null ? null : Password.Hashed.of(record.getPasswordHash()))
                .username(record.getUsername())
                .displayName(record.getDisplayName())
                .roles(roles)
                .emailVerified(Boolean.TRUE.equals(record.getEmailVerified()))
                .googleId(record.getGoogleSubject())
                .createDate(record.getCreateDate())
                .updateDate(record.getUpdateDate())
                .lastLoginAt(record.getLastLoginAt())
                .build();
    }
}
