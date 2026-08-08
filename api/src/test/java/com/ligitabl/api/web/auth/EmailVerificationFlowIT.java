package com.ligitabl.api.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.ligitabl.api.client.TurnstileClient;
import com.ligitabl.api.client.turnstile.TurnstileVerifyResponse;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.EmailVerificationToken;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.EmailVerificationTokenRepo;
import com.ligitabl.model.repo.UserRepo;

/**
 * Full-stack coverage of the email-verification web flow: GET /auth/verify-email page states and
 * POST /profile/resend-verification. Registration goes through the real web form (Turnstile
 * mocked, same rationale as AuthControllerRegisterFormIT), so tokens are created by the real
 * post-registration hook. The impersonation-blocked path (403) is exercised live
 *  — wiring an admin + impersonation session through TestRestTemplate isn't worth the
 * plumbing here given the guard is the handler's first line, identical to set-password.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmailVerificationFlowIT extends AbstractPostgresIT {

    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    /**
     * Session-sensitive calls need this client: the injected TestRestTemplate auto-follows
     * redirects, which both swallows the login response's Set-Cookie headers (the followed
     * request gets a fresh anonymous session) and hides the 302s these tests assert on.
     */
    private final org.springframework.web.client.RestTemplate web = buildNoRedirectTemplate();

    private static org.springframework.web.client.RestTemplate buildNoRedirectTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
                    throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        var template = new org.springframework.web.client.RestTemplate(factory);
        template.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepo userRepo;

    @Autowired
    EmailVerificationTokenRepo tokenRepo;

    @MockBean
    TurnstileClient turnstileClient;

    @Autowired
    org.jooq.DSLContext dsl;

    /**
     * The application's own {@code Clock} bean (systemUTC), not a frozen one: this test drives the
     * real registration flow end to end, so the token it reads back was minted from this same bean
     * milliseconds earlier.
     */
    @Autowired
    Clock clock;

    private static final UUID COMPETITION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID SEASON_ID = UUID.fromString("00000000-0000-0000-0000-00000000005e");

    @BeforeEach
    void setUp() {
        given(turnstileClient.isEnabled()).willReturn(true);
        given(turnstileClient.verify(any(), any()))
                .willReturn(Either.right(new TurnstileVerifyResponse(true, List.of(), null, null, null, null)));
        seedActiveSeason();
    }

    /**
     * NavbarControllerAdvice resolves the active season on every authenticated page render and
     * throws without one — logged-in views are untestable against a bare migrated schema.
     */
    private void seedActiveSeason() {
        var competition = com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;
        var season = com.ligitabl.model.db.tables.TSeason.T_SEASON;

        // The competition is resolved by SLUG, not assumed to be at COMPETITION_ID.
        //
        // Every IT shares one Testcontainers Postgres, and several seed their own
        // "premier-league" competition under a random PK (InPlaySeasonFixture, SegmentResultsChainIT)
        // without cleaning up. c_slug is unique, so an insert pinned to COMPETITION_ID then
        // conflicts on the slug and onConflictDoNothing() silently does nothing — leaving no row at
        // COMPETITION_ID for the season's FK to reference.
        UUID competitionId = dsl.select(competition.PK_ID)
                .from(competition)
                .where(competition.C_SLUG.eq("premier-league"))
                .fetchOptional(competition.PK_ID)
                .orElse(COMPETITION_ID);

        dsl.insertInto(competition)
                .set(competition.PK_ID, competitionId)
                .set(competition.C_NAME, "Premier League")
                .set(competition.C_SLUG, "premier-league")
                .set(competition.C_CODE, "PL")
                .onConflictDoNothing()
                .execute();

        // Same trap as the competition above, one level down. Every IT that seeds a season now takes
        // its slug from TestCalendar, so they all seed the *same* slug — and c_slug is unique, so an
        // insert pinned to SEASON_ID conflicts with whichever IT got there first and
        // onConflictDoNothing() silently does nothing, leaving no row at SEASON_ID for the
        // active-season pointer below. Resolve by slug first, exactly as the competition does.
        UUID seasonId = dsl.select(season.PK_ID)
                .from(season)
                .where(season.C_SLUG.eq(TestCalendar.SEASON_SLUG))
                .and(season.FK_COMPETITION_ID.eq(competitionId))
                .fetchOptional(season.PK_ID)
                .orElse(SEASON_ID);

        dsl.insertInto(season)
                .set(season.PK_ID, seasonId)
                .set(season.C_CLIENT_ID, 1)
                .set(season.FK_COMPETITION_ID, competitionId)
                .set(season.C_NAME, TestCalendar.SEASON_NAME)
                .set(season.C_SLUG, TestCalendar.SEASON_SLUG)
                .set(season.C_START_DATE, TestCalendar.SEASON_START)
                .set(season.C_END_DATE, TestCalendar.SEASON_END)
                .set(season.C_MAX_ROUNDS, 38)
                .set(season.C_CURRENT_MATCH_DAY, 0)
                .onConflictDoNothing()
                .execute();

        // Point the competition at this class's season regardless of which one it already had:
        // NavbarControllerAdvice resolves the active season on every authenticated render, and a
        // leftover season from another IT may since have been truncated away.
        dsl.update(competition)
                .set(competition.FK_ACTIVE_SEASON_ID, seasonId)
                .where(competition.PK_ID.eq(competitionId))
                .execute();
    }

    // ---------------------------------------------------------------- GET /auth/verify-email

    @Test
    void verifyEmail_validToken_verifiesUserAndShowsSuccess() {
        User user = registerUser();
        String token = latestToken(user).getToken();

        ResponseEntity<String> response = get("/auth/verify-email?token=" + token);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Email verified!");

        User reloaded = userRepo.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isEmailVerified()).isTrue();
        assertThat(reloaded.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void verifyEmail_reusedToken_showsAlreadyUsed() {
        User user = registerUser();
        String token = latestToken(user).getToken();

        get("/auth/verify-email?token=" + token);
        ResponseEntity<String> second = get("/auth/verify-email?token=" + token);

        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getBody()).contains("Link already used");
    }

    @Test
    void verifyEmail_unknownToken_showsInvalidAndChangesNothing() {
        User user = registerUser();

        ResponseEntity<String> response = get("/auth/verify-email?token=" + UUID.randomUUID());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Invalid link");
        assertThat(userRepo.findById(user.getId()).orElseThrow().isEmailVerified())
                .isFalse();
    }

    @Test
    void verifyEmail_missingToken_showsInvalid() {
        ResponseEntity<String> response = get("/auth/verify-email");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Invalid link");
    }

    @Test
    void verifyEmail_expiredToken_showsExpiredAndKeepsUserUnverified() {
        User user = registerUser();
        tokenRepo.deleteAllForUser(user.getId());

        var past = java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS);
        EmailVerificationToken expired = EmailVerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(user.getId())
                .createdAt(past)
                .expiresAt(past.plus(48, java.time.temporal.ChronoUnit.HOURS))
                .used(false)
                .usedAt(null)
                .build();
        tokenRepo.save(expired);

        ResponseEntity<String> response = get("/auth/verify-email?token=" + expired.getToken());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Link expired");
        assertThat(userRepo.findById(user.getId()).orElseThrow().isEmailVerified())
                .isFalse();
    }

    // ------------------------------------------------------ POST /profile/resend-verification

    @Test
    void resendVerification_requiresAuthentication() {
        User user = registerUser();
        long tokensBefore = tokenRepo.findLatestForUser(user.getId()).stream().count();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = web.exchange(
                url("/profile/resend-verification"),
                HttpMethod.POST,
                new HttpEntity<>(new LinkedMultiValueMap<String, String>(), headers),
                String.class);

        // Rejected before the handler runs: CSRF filter (403) or auth redirect — never 200.
        assertThat(response.getStatusCode().value()).isIn(302, 403);
        assertThat(tokenRepo.findLatestForUser(user.getId()).stream().count()).isEqualTo(tokensBefore);
    }

    @Test
    void resendVerification_insideCooldown_createsNoNewToken() {
        User user = registerUser();
        Session session = login(user.getEmail().value());
        // Registration created a token moments ago — the resend must hit the cooldown.
        String registrationToken = latestToken(user).getToken();

        postResend(session);

        assertThat(settingsPage(session)).contains("sent recently");
        assertThat(latestToken(user).getToken()).isEqualTo(registrationToken);
    }

    @Test
    void resendVerification_afterCooldown_invalidatesOldAndCreatesNewToken() {
        User user = registerUser();
        Session session = login(user.getEmail().value());
        // Clear the registration token so the cooldown (keyed on newest token) can't fire.
        tokenRepo.deleteAllForUser(user.getId());

        postResend(session);

        assertThat(settingsPage(session)).contains("Verification email sent to");
        EmailVerificationToken fresh = latestToken(user);
        assertThat(fresh.isValid(clock.instant())).isTrue();
        assertThat(fresh.getUserId()).isEqualTo(user.getId());
    }

    @Test
    void resendVerification_alreadyVerified_createsNoToken() {
        User user = registerUser();
        Session session = login(user.getEmail().value());
        get("/auth/verify-email?token=" + latestToken(user).getToken());
        tokenRepo.deleteAllForUser(user.getId());

        postResend(session);

        assertThat(settingsPage(session)).contains("already verified");
        assertThat(tokenRepo.findLatestForUser(user.getId())).isEmpty();
    }

    // ------------------------------------------------------------------------------- helpers

    private User registerUser() {
        String email = "verify-flow-" + UUID.randomUUID() + "@example.com";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", email);
        form.add("displayName", "Verify Flow User");
        form.add("password", "testPassword123");
        form.add("confirmPassword", "testPassword123");
        form.add("cf-turnstile-response", "test-token");

        restTemplate.exchange(url("/auth/register"), HttpMethod.POST, new HttpEntity<>(form, headers), String.class);

        return userRepo.findByEmail(Email.create(email)).orElseThrow();
    }

    private EmailVerificationToken latestToken(User user) {
        return tokenRepo.findLatestForUser(user.getId()).orElseThrow();
    }

    /** Form login is CSRF-ignored (SecurityConfig), so a session needs only the login POST. */
    private Session login(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", email);
        form.add("password", "testPassword123");

        ResponseEntity<String> response =
                web.exchange(url("/auth/login"), HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
        assertThat(response.getStatusCode().value())
                .as("login must redirect on success")
                .isEqualTo(302);

        // The login response sets several cookies (remember-me among them); only the session
        // cookie carries the authenticated session. Last one wins if fixation protection
        // reissues it.
        //
        // Both names are accepted deliberately. Sessions live in Postgres via Spring Session
        // (spring.session.store-type=jdbc), whose cookie is SESSION — not the servlet
        // container's JSESSIONID. This test matched only JSESSIONID and so failed on every
        // login from the moment Spring Session was introduced. The failure message lists what
        // actually arrived, so the next rename is a one-run diagnosis rather than an
        // investigation.
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).as("login must set cookies").isNotNull();
        String sessionCookie = setCookies.stream()
                .filter(c -> c.startsWith("SESSION=") || c.startsWith("JSESSIONID="))
                .map(c -> c.split(";")[0])
                .reduce((first, second) -> second)
                .orElseThrow(
                        () -> new AssertionError("login must establish a session; cookies received: " + setCookies));

        String settingsBody = settingsPage(new Session(sessionCookie, null));
        Matcher matcher = CSRF_PATTERN.matcher(settingsBody);
        assertThat(matcher.find()).as("settings page must carry a CSRF input").isTrue();

        return new Session(sessionCookie, matcher.group(1));
    }

    /** The flash renders on the next {@link #settingsPage} fetch, not on this response. */
    private void postResend(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, session.cookie());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", session.csrf());

        ResponseEntity<String> response = web.exchange(
                url("/profile/resend-verification"), HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/profile/settings");
    }

    private String settingsPage(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.cookie());
        ResponseEntity<String> response =
                web.exchange(url("/profile/settings"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().value())
                .as("session cookie must authenticate — anything else redirects to login")
                .isEqualTo(200);
        return response.getBody();
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.getForEntity(url(path), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private record Session(String cookie, String csrf) {}
}
