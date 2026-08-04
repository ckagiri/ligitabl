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
     * Users with no prediction for the season who have been seen since {@code activeSince} —
     * intended to be called with the season's {@code preSeasonOpensAt}.
     *
     * <p>That anchor is the point: it asks "have you opened the app at any time since this
     * season became joinable?". Someone who has, saw the season and chose not to submit — a
     * default table is the right thing to hand them. Someone who has not was never asked, and
     * a rolling "last N days" window would sweep them in purely because N happened to reach
     * back past {@code preSeasonOpensAt}. What they need is an invitation, not a silent entry.
     *
     * <p>Strictly wider than {@link #findUnjoinedUserIdsAfter} given the same anchor, since
     * {@code COALESCE(last_login_at, update_date) >= create_date} always holds: everyone who
     * signed up during this season has also been seen during it.
     *
     * <p>Deliberately applies no email predicates, unlike {@link
     * #findMailableUsersWithPreSeasonRegistration}: being auto-joined is participation, not
     * correspondence, so an unverified or opted-out account still gets a table.
     *
     * @param activeSince exclusive lower bound on last-seen; must not be null
     */
    List<UUID> findUnjoinedUserIdsActiveSince(UUID seasonId, OffsetDateTime activeSince);

    /**
     * Verified, non-opted-out users holding a round-0 (pre-season registration) prediction for the
     * season — auto-joined and genuinely pre-registered alike.
     *
     * <p>Round-0 is the whole filter: those rows still merge in with a full initial swap
     * allowance, which is what the welcome email tells them. A prediction at round ≥ 1 is already
     * committed and gets only the smaller round-opening allowance, so including it would overstate
     * what the user can still do.
     */
    List<User> findMailableUsersWithPreSeasonRegistration(UUID seasonId);

    /**
     * @param dueCutoff old enough to be due for the earliest reminder stage
     * @param staleCutoff exclusive lower bound; users not seen since before this are excluded as
     *     too dormant to bother reminding
     */
    List<User> findUnjoinedUsersRegisteredBefore(UUID seasonId, OffsetDateTime dueCutoff, OffsetDateTime staleCutoff);
}
