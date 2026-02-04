package com.ligitabl.api.web.shared.error;

import jakarta.servlet.http.HttpServletResponse;

public final class UseCaseErrorStatusMapper {

    private UseCaseErrorStatusMapper() {
    }

    public static int toHttpStatus(com.ligitabl.api.shared.errors.UseCaseError error) {
        if (error == null) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        if (error instanceof com.ligitabl.api.shared.errors.ValidationError) {
            return HttpServletResponse.SC_BAD_REQUEST;
        }
        if (error instanceof com.ligitabl.api.shared.errors.NotFoundError) {
            return HttpServletResponse.SC_NOT_FOUND;
        }
        if (error instanceof com.ligitabl.api.shared.errors.ConflictError) {
            return HttpServletResponse.SC_CONFLICT;
        }
        if (error instanceof com.ligitabl.api.shared.errors.UnprocessableEntityError) {
            return 422;
        }
        if (error instanceof com.ligitabl.api.shared.errors.AuthenticationError) {
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        if (error instanceof com.ligitabl.api.shared.errors.AuthorizationError) {
            return HttpServletResponse.SC_FORBIDDEN;
        }
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }
}
