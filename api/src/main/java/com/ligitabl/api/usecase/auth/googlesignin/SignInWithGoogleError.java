package com.ligitabl.api.usecase.auth.googlesignin;

public sealed interface SignInWithGoogleError {
    /** The email's account is already linked to a different Google account. */
    record EmailLinkedToOtherGoogleAccount(String email) implements SignInWithGoogleError {}
}
