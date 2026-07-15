package com.ligitabl.api.auth.impersonation;

/**
 * Per-request view of who is really logged in ({@code original}) and who the request
 * should behave as ({@code effective}). Set as a request attribute by
 * {@link ImpersonationSessionFilter} and read via {@link CurrentUserFacade}.
 */
public record ImpersonationContext(UserSummary original, UserSummary effective, boolean impersonating) {

    public static final String REQUEST_ATTRIBUTE = "LIGITABL_IMPERSONATION_CONTEXT";

    public static ImpersonationContext notImpersonating(UserSummary user) {
        return new ImpersonationContext(user, user, false);
    }

    public static ImpersonationContext impersonating(UserSummary original, UserSummary effective) {
        return new ImpersonationContext(original, effective, true);
    }
}
