package com.ligitabl.seed.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static com.ligitabl.seed.internal.util.SeedCoercions.asString;

public class UserSeeder extends AbstractSeeder<List<Map<String, Object>>> {

    private static final Table<Record> T_USER = DSL.table(DSL.name("t_user"));

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    public UserSeeder(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected boolean isValidConfig(List<Map<String, Object>> users) {
        return users != null && !users.isEmpty();
    }

    @Override
    protected void performSeed(List<Map<String, Object>> users) {
        for (Map<String, Object> user : users) {
            seedUser(user);
        }
    }

    @Override
    protected String getSeederName() {
        return "user";
    }

    private void seedUser(Map<String, Object> user) {
        String email = asString(user.get("email"));
        String password = asString(user.get("password"));
        String displayName = asString(user.get("displayName"));

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("User entry missing email: " + user);
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("User entry missing password for email: " + email);
        }

        String passwordHash = PASSWORD_ENCODER.encode(password);

        int rowsAffected = dsl.insertInto(T_USER)
                .columns(
                        DSL.field(DSL.name("pk_id"), UUID.class),
                        DSL.field(DSL.name("c_email"), String.class),
                        DSL.field(DSL.name("c_password_hash"), String.class),
                        DSL.field(DSL.name("c_display_name"), String.class))
                .values(UUID.randomUUID(), email, passwordHash, displayName)
                .onConflict(DSL.field(DSL.name("c_email")))
                .doNothing()
                .execute();

        if (rowsAffected > 0) {
            recordInsert();
        } else {
            recordSkip();
        }
    }
}
