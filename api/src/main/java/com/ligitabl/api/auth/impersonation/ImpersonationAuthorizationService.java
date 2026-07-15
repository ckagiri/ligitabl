package com.ligitabl.api.auth.impersonation;

import com.ligitabl.model.domain.User;

public interface ImpersonationAuthorizationService {

    /**
     * Decide whether {@code original} may impersonate the user identified by
     * {@code identifier} (email when it contains '@', username otherwise).
     */
    Result assertCanImpersonate(User original, String identifier);

    sealed interface Result
            permits Result.Ok, Result.NotAdmin, Result.TargetNotFound, Result.SelfImpersonation,
                    Result.TargetPrivileged {

        record Ok(User target) implements Result {}

        record NotAdmin() implements Result {}

        record TargetNotFound(String identifier) implements Result {}

        record SelfImpersonation() implements Result {}

        record TargetPrivileged(String identifier) implements Result {}
    }
}
