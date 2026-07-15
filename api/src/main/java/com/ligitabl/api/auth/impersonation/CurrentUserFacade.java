package com.ligitabl.api.auth.impersonation;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Read-side access to the current request's {@link ImpersonationContext}.
 * Empty outside a request or when the request is unauthenticated.
 */
@Component
public class CurrentUserFacade {

    public Optional<UserSummary> getOriginalUser() {
        return context().map(ImpersonationContext::original);
    }

    public Optional<UserSummary> getEffectiveUser() {
        return context().map(ImpersonationContext::effective);
    }

    public boolean isImpersonating() {
        return context().map(ImpersonationContext::impersonating).orElse(false);
    }

    private Optional<ImpersonationContext> context() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        Object value = attributes.getRequest().getAttribute(ImpersonationContext.REQUEST_ATTRIBUTE);
        return value instanceof ImpersonationContext context ? Optional.of(context) : Optional.empty();
    }
}
