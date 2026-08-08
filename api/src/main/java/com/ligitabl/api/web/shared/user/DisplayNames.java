package com.ligitabl.api.web.shared.user;

/**
 * Cleans user-chosen display names for presentation.
 *
 * <p>⚠️ <b>Not an XSS defence.</b> Every template renders names through {@code th:text} or
 * {@code th:attr}, both of which Thymeleaf escapes, and there is no {@code th:utext} on a display
 * name anywhere in the codebase. A name containing markup is already inert — it simply renders as
 * literal text. This class exists because that literal text is *ugly*: a name like
 * {@code <script>x</script>} shows up verbatim in leaderboards, and would be painted verbatim onto
 * the share card canvas, where nothing escapes anything because it is pixels.
 *
 * <p>Registration validates display names for length only — {@code @NotBlank} and
 * {@code @Size(3, 30)} on {@code ProfileController.ProfileForm} — so anything inside 30 characters
 * is already stored today. Sanitising on read rather than tightening that constraint is deliberate:
 * a stricter rule cannot retroactively fix rows that already exist, and rejecting a name someone
 * has been using for months is a worse experience than quietly rendering it sensibly.
 */
public final class DisplayNames {

    private DisplayNames() {}

    /**
     * Elements whose contents are code rather than text.
     *
     * <p>Other tags keep their inner text untouched — {@code <b>Ann</b>} is someone trying to look
     * bold, and the name they meant is "Ann". For these, the contents are a script body or a
     * stylesheet, so they are salvaged rather than kept verbatim (see {@link #clean}).
     */
    private static final String CODE_BEARING_TAGS = "script|style|iframe|object|embed|noscript|template";

    /** Punctuation that makes salvaged text read as code rather than as a name. */
    private static final String CODE_PUNCTUATION = "[(){}\\[\\];=+*/\\\\|`$@#%^~\"]";

    /**
     * Strips markup and collapses whitespace, so a name reads as a name.
     *
     * <p>Contents of a code-bearing tag are <em>salvaged</em>, not discarded:
     * {@code <script>alert(1)</script>} becomes "alert 1" rather than nothing, so a personalised
     * card is still possible. The punctuation that made it look like code is removed, which is what
     * separates this from simply keeping the payload — "alert(1)'s final table prediction" reads as
     * a deliberate joke, where "alert 1" just reads as an odd name, which is what it is.
     *
     * <p>A stray {@code <} that is not part of a recognisable tag is left alone. {@code Ann <b Bo}
     * is far more likely to be a name with an odd character than a broken tag, and silently
     * deleting a letter from someone's name is worse than rendering the bracket they typed.
     *
     * @return the cleaned name, or null if nothing legible survives
     */
    public static String clean(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        String cleaned = displayName
                // Code-bearing elements: drop the tags, keep the inner text for salvage below.
                .replaceAll("(?is)<\\s*(" + CODE_BEARING_TAGS + ")\\b[^>]*>", " ")
                .replaceAll("(?is)<\\s*/\\s*(" + CODE_BEARING_TAGS + ")\\s*>", " ")
                // Any other well-formed tag: drop the tag, keep what it wrapped.
                .replaceAll("(?is)<\\s*/?\\s*[a-z][a-z0-9]*\\b[^>]*>", " ")
                // Strip the punctuation that reads as code. Apostrophes, hyphens, commas and full
                // stops survive — they belong in real names (O'Brien-Smith, "Jr.").
                .replaceAll(CODE_PUNCTUATION, " ")
                .replaceAll("\\s+", " ")
                .trim();

        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * As {@link #clean}, with a caller-supplied fallback for names that clean away to nothing.
     *
     * <p>Callers deliberately pass their own generic wording rather than an identifier: the share
     * card must never fall back to an email address, which {@code WebUserDetails.getDisplayName()}
     * does by design for in-app UI. That is fine on a page the user is already looking at, and a
     * privacy leak on an image they post publicly.
     */
    public static String cleanOr(String displayName, String fallback) {
        String cleaned = clean(displayName);
        return cleaned == null ? fallback : cleaned;
    }

    /**
     * The possessive form of a name: {@code "Ann"} → {@code "Ann's"}, {@code "Charles"} →
     * {@code "Charles's"}.
     *
     * <p>Always {@code 's}, including after a trailing s. Both forms are defensible — Chicago says
     * "Charles's", AP says "Charles'" — and this follows Chicago because it matches how the name is
     * actually said aloud. More practically it is one rule with no exceptions: deciding whether a
     * trailing s marks a plural ("the Rovers' table") or a singular ("Charles's table") cannot be
     * done from a display name, so any attempt to be clever would just be wrong differently.
     */
    public static String possessive(String name) {
        return name == null ? null : name + "'s";
    }
}
