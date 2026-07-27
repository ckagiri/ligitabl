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

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * Set or clear (null) a user's username.
     */
    void updateUsername(UUID userId, String username);

    User create(User model);

    void update(User user);

    boolean existsByEmail(Email email);

    void updatePassword(UUID userId, Password.Hashed password);

    void markEmailVerified(UUID userId, OffsetDateTime verifiedAt);

    List<User> findAllPaged(int offset, int limit);

    long countAll();

    void updateLastLoginAt(UUID userId, OffsetDateTime at);

    void delete(UUID userId);

    List<UUID> findUnjoinedUserIdsAfter(UUID seasonId, OffsetDateTime registeredAfter);

    /**
     * @param dueCutoff old enough to be due for the earliest reminder stage
     * @param staleCutoff exclusive lower bound; users not seen since before this are excluded as
     *     too dormant to bother reminding
     */
    List<User> findUnjoinedUsersRegisteredBefore(UUID seasonId, OffsetDateTime dueCutoff, OffsetDateTime staleCutoff);
}
