package com.ligitabl.model.repo;

import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.User;

public interface UserRepo {
    Optional<User> findById(UUID id);

    Optional<User> findByEmail(Email email);

    Optional<User> findByPublicId(PublicId publicId);

    User create(User model);

    boolean existsByEmail(Email email);

    void updatePassword(UUID userId, Password.Hashed password);
}
