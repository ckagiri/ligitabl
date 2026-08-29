package com.ligitabl.api.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Covers the URI → navbar section mapping.
 *
 * <p>{@code activeNav} reads only the request, so the advice's collaborators are irrelevant here
 * and are left null rather than mocked.
 */
class NavbarActiveNavTest {

    private final NavbarControllerAdvice advice =
            new NavbarControllerAdvice(null, null, null, null, null, null, null, null, null);

    private String sectionFor(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return advice.activeNav(request);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        // Rounds: the section lives in the last segment, and the round number varies as the
        // hx-push-url pager moves. This is why sections are keyed rather than compared to hrefs.
        "/rounds/current/standings, standings",
        "/rounds/7/standings, standings",
        "/rounds/current/matches, matches",
        "/rounds/12/matches, matches",
        // Sub-resources stay within their section.
        "/contests, contests",
        "/contests/create, contests",
        "/contests/3f1a5b2c-0000-4000-8000-000000000000/members, contests",
        "/my-table, mytable",
        "/my-table/guest, mytable",
        "/my-table/what-if, mytable",
        // /my-table forwards to these, and a forward rewrites the request URI, so the advice
        // never sees /my-table on a real request — only the forward target.
        "/predictions/user/me, mytable",
        "/predictions/user/guest, mytable",
        "/predictions/user/what-if, mytable",
        "/leaderboard, leaderboard",
        // The two near-collisions, in both directions.
        "/final-table/leaderboard, finaltable",
        "/faq/final-table, faq",
        // Home is reachable by two paths.
        "/, home",
        "/home, home",
        "/about, about",
        "/faq, faq",
        "/admin/seasons, admin-seasons",
        "/admin/users, admin-users",
        "/admin/matches, admin-matches",
    })
    void mapsUriToSection(String uri, String expected) {
        assertThat(sectionFor(uri)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> null")
    @CsvSource({
        "/auth/login",
        "/auth/register",
        // A round URL that is neither standings nor matches owns no nav item.
        "/rounds/4",
        // Boundary check: these merely start with a section prefix, so they must not match it.
        // A `startsWith` "simplification" of matchesSection would break exactly here.
        "/about-us",
        "/contestsomething",
        "/faqs",
    })
    void hasNoSectionForUnmappedPaths(String uri) {
        assertThat(sectionFor(uri)).isNull();
    }

    @Test
    void stripsContextPathBeforeMatching() {
        // No environment sets a context path today, so this test is the specification.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/app");
        request.setRequestURI("/app/leaderboard");

        assertThat(advice.activeNav(request)).isEqualTo("leaderboard");
    }

    @Test
    void hasNoSectionWithoutARequest() {
        assertThat(advice.activeNav(null)).isNull();
    }
}
