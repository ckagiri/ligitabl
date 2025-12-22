package com.ligitabl.model.infra;

import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepo {

    private final DSLContext dsl;

    private static final Table<Record> T_USER = DSL.table(DSL.name("t_user"));

    @Override
    public Optional<User> findById(UUID id) {
        var record = dsl.selectFrom(T_USER)
                .where(DSL.field(DSL.name("pk_id"), UUID.class).eq(id))
                .fetchOne();

        return Optional.ofNullable(map(record));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        var record = dsl.selectFrom(T_USER)
                .where(DSL.field(DSL.name("c_email"), String.class).eq(email))
                .fetchOne();

        return Optional.ofNullable(map(record));
    }

    @Override
    public User create(User model) {
        if (model.getId() == null) {
            throw new IllegalArgumentException("User.id must not be null on create");
        }

        int rows = dsl.insertInto(T_USER)
                .columns(
                        DSL.field(DSL.name("pk_id"), UUID.class),
                        DSL.field(DSL.name("c_email"), String.class),
                        DSL.field(DSL.name("c_password_hash"), String.class),
                        DSL.field(DSL.name("c_display_name"), String.class))
                .values(model.getId(), model.getEmail(), model.getPasswordHash(), model.getDisplayName())
                .execute();

        if (rows != 1) {
            throw new IllegalStateException("Expected to insert 1 row, inserted=" + rows);
        }

        return model;
    }

    private static User map(Record record) {
        if (record == null) {
            return null;
        }

        return User.builder()
                .id(record.get(DSL.field(DSL.name("pk_id"), UUID.class)))
                .email(record.get(DSL.field(DSL.name("c_email"), String.class)))
                .passwordHash(record.get(DSL.field(DSL.name("c_password_hash"), String.class)))
                .displayName(record.get(DSL.field(DSL.name("c_display_name"), String.class)))
                .build();
    }
}
