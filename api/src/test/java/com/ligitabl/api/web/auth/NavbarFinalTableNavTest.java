package com.ligitabl.api.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ligitabl.api.auth.impersonation.CurrentUserFacade;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.finaltable.shared.FinalTableSupport;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EmailVerificationTokenRepo;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

/** Covers who is offered the Final Table link in the navbar. */
@ExtendWith(MockitoExtension.class)
class NavbarFinalTableNavTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private ContestRepo contestRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private UserRepo userRepo;

    @Mock
    private CurrentUserFacade currentUserFacade;

    @Mock
    private FinalTableSupport finalTableSupport;

    @Mock
    private FinalTablePredictionRepo predictionRepo;

    @Mock
    private EmailVerificationTokenRepo emailVerificationTokenRepo;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC);

    private NavbarControllerAdvice advice;
    private Season season;
    private Principal principal;

    @BeforeEach
    void setUp() {
        season = Season.builder().id(UUID.randomUUID()).build();
        principal = () -> "player@example.com";
        advice = new NavbarControllerAdvice(
                contestRepo,
                seasonRepo,
                competitionDefaults,
                userRepo,
                currentUserFacade,
                finalTableSupport,
                predictionRepo,
                emailVerificationTokenRepo,
                clock);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "player@example.com", "n/a", AuthorityUtils.createAuthorityList("ROLE_PLAYER")));
    }

    private void signOut() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    /**
     * Signs in with a WebUserDetails principal so resolveUserId short-circuits to its id — the
     * email lookup takes an Email value object and is not the path under test here.
     */
    private UUID signInWithId() {
        UUID userId = UUID.randomUUID();
        WebUserDetails details = new WebUserDetails(
                userId,
                "abc1234567",
                "player@example.com",
                "Player",
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_PLAYER"));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
        return userId;
    }

    @Test
    void aSignedInPlayerSeesTheLinkWhileEntryIsOpen() {
        // Open season: anyone can go and make a table, so no ownership check is needed.
        signIn();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(finalTableSupport.isEntryOpen(season)).thenReturn(true);

        assertThat(advice.showFinalTableNav(principal)).isTrue();
        verifyNoInteractions(predictionRepo);
    }

    @Test
    void aGuestSeesTheLinkWhileEntryIsOpen() {
        signOut();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(finalTableSupport.isEntryOpen(season)).thenReturn(true);

        assertThat(advice.showFinalTableNav(principal)).isTrue();
    }

    @Test
    void aGuestDoesNotSeeTheLinkOnceEntryIsClosed() {
        // Round 1 is locked or the season is done: pointing a guest at a game they can no longer
        // join is worse than staying quiet.
        signOut();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(finalTableSupport.isEntryOpen(season)).thenReturn(false);

        assertThat(advice.showFinalTableNav(principal)).isFalse();
    }

    @Test
    void aPlayerWithALockedTableStillSeesTheLink() {
        // Entry closed, but they have a table — it is theirs to revisit, locked or scored.
        UUID userId = signInWithId();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(finalTableSupport.isEntryOpen(season)).thenReturn(false);
        when(predictionRepo.findByUserAndSeason(userId, season.getId()))
                .thenReturn(Optional.of(FinalTablePrediction.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .seasonId(season.getId())
                        .build()));

        assertThat(advice.showFinalTableNav(principal)).isTrue();
    }

    @Test
    void aPlayerWhoNeverEnteredDoesNotSeeTheLinkOnceEntryIsClosed() {
        // The case an earlier version got wrong: signed in, but no table and no way to make one.
        // The link would be a dead end — the same problem the guest rule already avoided.
        UUID userId = signInWithId();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(finalTableSupport.isEntryOpen(season)).thenReturn(false);
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());

        assertThat(advice.showFinalTableNav(principal)).isFalse();
    }

    @Test
    void hidesTheLinkForAGuestWhenNoSeasonCanBeResolved() {
        // getActiveSeason() throws here; a nav item that 503s is worse than an absent one.
        signOut();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());

        assertThat(advice.showFinalTableNav(principal)).isFalse();
    }

    // --- finalTableEntryOpen: for prompts that invite someone to go and make a table -------

    @Test
    void entryOpenIsFalseForAPlayerWhoseTableIsLockedEvenThoughTheLinkStays() {
        // The distinction between the two: a player with a locked table keeps the nav link, because
        // visiting it is still worth it — but the "go predict the final table" prompt must not
        // appear, because there is nothing left to enter.
        UUID userId = signInWithId();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(finalTableSupport.isEntryOpen(season)).thenReturn(false);
        when(predictionRepo.findByUserAndSeason(userId, season.getId()))
                .thenReturn(Optional.of(FinalTablePrediction.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .seasonId(season.getId())
                        .build()));

        assertThat(advice.showFinalTableNav(principal)).isTrue();
        assertThat(advice.finalTableEntryOpen()).isFalse();
    }

    @Test
    void entryOpenIsTrueWhileTheGameStillAcceptsTables() {
        signIn();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(finalTableSupport.isEntryOpen(season)).thenReturn(true);

        assertThat(advice.finalTableEntryOpen()).isTrue();
    }

    @Test
    void entryOpenIsFalseWhenNoSeasonCanBeResolved() {
        signIn();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());

        assertThat(advice.finalTableEntryOpen()).isFalse();
    }
}
