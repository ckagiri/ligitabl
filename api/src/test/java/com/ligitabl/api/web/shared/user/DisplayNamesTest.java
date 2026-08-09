package com.ligitabl.api.web.shared.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisplayNamesTest {

    @Test
    void leavesOrdinaryNamesAlone() {
        assertThat(DisplayNames.clean("CharlesKagiri")).isEqualTo("CharlesKagiri");
        assertThat(DisplayNames.clean("kagiri wagner")).isEqualTo("kagiri wagner");
        assertThat(DisplayNames.clean("O'Brien-Smith")).isEqualTo("O'Brien-Smith");
        assertThat(DisplayNames.clean("Zoë 42")).isEqualTo("Zoë 42");
    }

    @Test
    void salvagesLegibleTextFromAScriptTag() {
        // The real case: a stored name that renders as literal markup in leaderboards and would be
        // painted verbatim onto the share canvas. Salvaging beats blanking — a personalised card is
        // still possible — but the code punctuation goes, so it reads as an odd name rather than as
        // a deliberate joke ("alert 1's prediction", not "alert(1)'s prediction").
        assertThat(DisplayNames.clean("<script>alert(1)</script>")).isEqualTo("alert 1");
        assertThat(DisplayNames.clean("Ann<script>alert(1)</script>")).isEqualTo("Ann alert 1");
    }

    @Test
    void anEmptyTagPairStillCleansAwayToNothing() {
        // Nothing to salvage: the caller falls back to its own generic wording.
        assertThat(DisplayNames.clean("<script></script>")).isNull();
        assertThat(DisplayNames.clean("<b></b>")).isNull();
    }

    @Test
    void salvagesFromAnUnclosedTagToo() {
        assertThat(DisplayNames.clean("<script>alert(1)")).isEqualTo("alert 1");
        assertThat(DisplayNames.clean("Ann<script>hi")).isEqualTo("Ann hi");
        // A stylesheet body is all punctuation, so nothing legible is left.
        assertThat(DisplayNames.clean("<style>body{}")).isEqualTo("body");
    }

    @Test
    void stripsPunctuationThatReadsAsCode() {
        assertThat(DisplayNames.clean("a=b+c")).isEqualTo("a b c");
        assertThat(DisplayNames.clean("x${y}")).isEqualTo("x y");
        assertThat(DisplayNames.clean("who@where")).isEqualTo("who where");
    }

    @Test
    void keepsPunctuationThatBelongsInRealNames() {
        assertThat(DisplayNames.clean("O'Brien-Smith")).isEqualTo("O'Brien-Smith");
        assertThat(DisplayNames.clean("Sammy Jr.")).isEqualTo("Sammy Jr.");
        assertThat(DisplayNames.clean("Lee, Ann")).isEqualTo("Lee, Ann");
    }

    @Test
    void keepsTheTextInsideHarmlessFormattingTags() {
        // <b>Ann</b> is someone trying to look bold, not an attack — the name they meant is "Ann".
        assertThat(DisplayNames.clean("<b>Ann</b>")).isEqualTo("Ann");
        assertThat(DisplayNames.clean("<i>Bo</i> <u>Jo</u>")).isEqualTo("Bo Jo");
    }

    @Test
    void stripsLoneTags() {
        assertThat(DisplayNames.clean("Ann<br/>Bo")).isEqualTo("Ann Bo");
        assertThat(DisplayNames.clean("Ann<br>Bo")).isEqualTo("Ann Bo");
    }

    @Test
    void leavesStrayBracketsThatAreNotTags() {
        // "Ann <b Bo" is far more likely a name with an odd character than broken markup, and
        // deleting the "b" would be silently mangling someone's name. Brackets are inert in every
        // render path, so keeping them costs nothing.
        assertThat(DisplayNames.clean("Ann <b Bo")).isEqualTo("Ann <b Bo");
        assertThat(DisplayNames.clean("a > b")).isEqualTo("a > b");
        assertThat(DisplayNames.clean("3 < 4")).isEqualTo("3 < 4");
    }

    @Test
    void collapsesWhitespaceLeftBehind() {
        assertThat(DisplayNames.clean("  Ann   Bo  ")).isEqualTo("Ann Bo");
        assertThat(DisplayNames.clean("Ann\t\nBo")).isEqualTo("Ann Bo");
    }

    @Test
    void returnsNullForNothingLegible() {
        assertThat(DisplayNames.clean(null)).isNull();
        assertThat(DisplayNames.clean("")).isNull();
        assertThat(DisplayNames.clean("   ")).isNull();
    }

    @Test
    void fallsBackOnlyWhenNothingSurvives() {
        assertThat(DisplayNames.cleanOr("Ann", "My Final Table")).isEqualTo("Ann");
        assertThat(DisplayNames.cleanOr("<script></script>", "My Final Table")).isEqualTo("My Final Table");
        assertThat(DisplayNames.cleanOr(null, "My Final Table")).isEqualTo("My Final Table");
    }

    @Test
    void neverFallsBackToAnEmailAddress() {
        // WebUserDetails.getDisplayName() falls back to email for in-app UI. That must not reach a
        // share card, which is an image people post publicly.
        assertThat(DisplayNames.cleanOr("", "My Final Table")).doesNotContain("@");
    }

    @Test
    void addsApostropheSEvenAfterATrailingS() {
        // Chicago, not AP: "Charles's" is how the name is said aloud, and one rule with no
        // exceptions beats guessing whether a trailing s marks a plural.
        assertThat(DisplayNames.possessive("Ann")).isEqualTo("Ann's");
        assertThat(DisplayNames.possessive("Charles")).isEqualTo("Charles's");
        assertThat(DisplayNames.possessive("Ross")).isEqualTo("Ross's");
        assertThat(DisplayNames.possessive(null)).isNull();
    }
}
