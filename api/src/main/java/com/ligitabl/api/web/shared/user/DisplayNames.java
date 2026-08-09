package com.ligitabl.api.web.shared.user;

/**
 * Cleans user-chosen display names for presentation.
 */
public final class DisplayNames {

    private DisplayNames() {}

    /**
     * Elements whose contents are code rather than text.
     */
    private static final String CODE_BEARING_TAGS = "script|style|iframe|object|embed|noscript|template";

    /** Punctuation that makes salvaged text read as code rather than as a name. */
    private static final String CODE_PUNCTUATION = "[(){}\\[\\];=+*/\\\\|`$@#%^~\"]";

    /**
     * Strips markup and collapses whitespace, so a name reads as a name.
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
     */
    public static String cleanOr(String displayName, String fallback) {
        String cleaned = clean(displayName);
        return cleaned == null ? fallback : cleaned;
    }

    /**
     * The possessive form of a name.
     */
    public static String possessive(String name) {
        return name == null ? null : name + "'s";
    }
}
