package com.ligitabl.api.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.finaltable.shared.FinalTableSupport;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.ContestRepo;
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

    private NavbarControllerAdvice advice;
    private Season season;

    @BeforeEach
    void setUp() {
        season = Season.builder().id(UUID.randomUUID()).build();
        advice = new NavbarControllerAdvice(
                contestRepo, seasonRepo, competitionDefaults, userRepo, currentUserFacade, finalTableSupport);
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

    @Test
    void aSignedInPlayerAlwaysSeesTheLink() {
        // Their table is theirs whether or not it is still editable — a locked or scored table is
        // the point of the game, so the season's state is not consulted at all.
        signIn();

        assertThat(advice.showFinalTableNav()).isTrue();
        verifyNoInteractions(finalTableSupport, seasonRepo);
    }

    @Test
    void aGuestSeesTheLinkWhileEntryIsOpen() {
        signOut();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(finalTableSupport.isEntryOpen(season)).thenReturn(true);

        assertThat(advice.showFinalTableNav()).isTrue();
    }

    @Test
    void aGuestDoesNotSeeTheLinkOnceEntryIsClosed() {
        // Round 1 is locked or the season is done: pointing a guest at a game they can no longer
        // join is worse than staying quiet.
        signOut();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(finalTableSupport.isEntryOpen(season)).thenReturn(false);

        assertThat(advice.showFinalTableNav()).isFalse();
    }

    @Test
    void hidesTheLinkForAGuestWhenNoSeasonCanBeResolved() {
        // getActiveSeason() throws here; a nav item that 503s is worse than an absent one.
        signOut();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());

        assertThat(advice.showFinalTableNav()).isFalse();
    }
}
