package com.ligitabl.api.usecase.account;

import java.util.Set;
import java.util.UUID;

import com.ligitabl.model.domain.identity.Email;
import com.ligitabl.model.domain.identity.Password;
import com.ligitabl.model.domain.identity.PublicId;
import com.ligitabl.model.domain.identity.Role;
import com.ligitabl.model.domain.identity.User;

public final class AccountTestUsers {

    public static final Password.Hashed HASHED = Password.Hashed.of("$2a$10$existingHash");

    private AccountTestUsers() {}

    public static User user(String googleId, Password.Hashed password) {
        return User.builder()
                .id(UUID.randomUUID())
                .publicId(PublicId.create("abcdefghjk"))
                .email(Email.create("alice@example.com"))
                .displayName("Alice")
                .roles(Set.of(Role.PLAYER))
                .googleId(googleId)
                .password(password)
                .build();
    }
}
