package com.ligitabl.api.auth.impersonation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by {@link ImpersonationGuard} when a sensitive action is attempted while
 * impersonating. {@code @ResponseStatus} maps it for {@code @Controller}s too —
 * {@code GlobalExceptionHandler} only covers {@code @RestController}s.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ImpersonationRestrictedException extends RuntimeException {

    public ImpersonationRestrictedException() {
        super("This action is not allowed while impersonating another user");
    }
}
