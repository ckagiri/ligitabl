package com.ligitabl.model.repo;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.User;

public interface UserRepo {
    Optional<User> findById(UUID id);

    Map<UUID, User> findByIds(Collection<UUID> ids);

    Map<UUID, String> findDisplayNamesByIds(Collection<UUID> ids);

    Optional<User> findByEmail(Email email);

    Optional<User> findByPublicId(PublicId publicId);

    Optional<User> findByGoogleId(String googleId);

    User create(User model);

    void update(User user);

    boolean existsByEmail(Email email);

    void updatePassword(UUID userId, Password.Hashed password);

    List<User> findAllPaged(int offset, int limit);

    long countAll();

    void updateLastLoginAt(UUID userId, OffsetDateTime at);
}
