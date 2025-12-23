package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TUser.T_USER;
import static com.ligitabl.model.db.tables.TUserRole.T_USER_ROLE;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.mindrot.jbcrypt.BCrypt;

import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.seed.internal.util.SeedCoercions;

public class UserSeeder {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PUBLIC_ID_ALPHABET =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz".toCharArray();

    private final DSLContext dsl;

    public UserSeeder(DSLContext dsl) {
        this.dsl = dsl;
    }

    public SeedResult seed(List<Map<String, Object>> users) {
        if (users == null || users.isEmpty()) {
            return new SeedResult("user", 0, 0);
        }

        int inserted = 0;
        int skipped = 0;

        for (Map<String, Object> user : users) {
            String email = SeedCoercions.asString(user.get("email"));
            String password = SeedCoercions.asString(user.get("password"));
            String displayName = SeedCoercions.asString(user.get("displayName"));
            String publicIdStr = SeedCoercions.asString(user.get("publicId"));
            Boolean emailVerified = SeedCoercions.asBoolean(user.get("emailVerified"));

            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("user.email is required");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("user.password is required (email=" + email + ")");
            }

            String publicId = ensurePublicId(publicIdStr);
            Set<String> roles = parseRoles(user.get("roles"));

            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(10));

            boolean created = upsertUser(email, passwordHash, displayName, publicId, emailVerified, roles);
            if (created) {
                inserted++;
            } else {
                skipped++;
            }
        }

        return new SeedResult("user", inserted, skipped);
    }

    private boolean upsertUser(
            String email,
            String passwordHash,
            String displayName,
            String publicId,
            Boolean emailVerified,
            Set<String> roles) {

        return dsl.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);

            var existing = tx.selectFrom(T_USER).where(T_USER.C_EMAIL.eq(email)).fetchOne();

            final UUID userId;
            final boolean created;

            if (existing == null) {
                userId = UUID.randomUUID();
                created = true;

                tx.insertInto(T_USER)
                        .set(T_USER.PK_ID, userId)
                        .set(T_USER.C_EMAIL, email)
                        .set(T_USER.C_PASSWORD_HASH, passwordHash)
                        .set(T_USER.C_DISPLAY_NAME, displayName)
                        .set(T_USER.C_PUBLIC_ID, publicId)
                        .set(T_USER.C_EMAIL_VERIFIED, emailVerified != null ? emailVerified : true)
                        .execute();
            } else {
                userId = existing.getId();
                created = false;

                tx.update(T_USER)
                        .set(T_USER.C_PASSWORD_HASH, passwordHash)
                        .set(T_USER.C_DISPLAY_NAME, displayName)
                        .set(T_USER.C_PUBLIC_ID, publicId)
                        .set(T_USER.C_EMAIL_VERIFIED, emailVerified != null ? emailVerified : existing.getEmailVerified())
                        .where(T_USER.PK_ID.eq(userId))
                        .execute();

                tx.deleteFrom(T_USER_ROLE).where(T_USER_ROLE.FK_USER_ID.eq(userId)).execute();
            }

            for (String role : roles) {
                tx.insertInto(T_USER_ROLE)
                        .set(T_USER_ROLE.FK_USER_ID, userId)
                        .set(T_USER_ROLE.C_ROLE, role)
                        .onConflictDoNothing()
                        .execute();
            }

            return created;
        });
    }

    private static Set<String> parseRoles(Object rolesObj) {
        Set<String> roles = new LinkedHashSet<>();
        if (rolesObj instanceof List<?> list) {
            for (Object raw : list) {
                String value = raw == null ? null : String.valueOf(raw);
                if (value == null || value.isBlank()) {
                    continue;
                }
                Role role = Role.fromString(value);
                roles.add(role.getValue());
            }
        }
        return roles;
    }

    private static String ensurePublicId(String publicId) {
        if (publicId != null && !publicId.isBlank()) {
            // Validate format early.
            return PublicId.create(publicId).value();
        }

        // Generate a valid 10-char public id.
        String generated;
        do {
            generated = randomPublicId();
        } while (!isValidPublicId(generated));

        return generated;
    }

    private static String randomPublicId() {
        char[] chars = new char[10];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = PUBLIC_ID_ALPHABET[RANDOM.nextInt(PUBLIC_ID_ALPHABET.length)];
        }
        return new String(chars);
    }

    private static boolean isValidPublicId(String value) {
        try {
            PublicId.create(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
