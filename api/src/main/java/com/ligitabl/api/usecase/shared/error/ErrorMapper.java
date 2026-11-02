package com.ligitabl.api.usecase.shared.error;

import com.ligitabl.api.shared.errors.*;

public class ErrorMapper {

    public static int toHttpStatus(UseCaseError error) {
        if (error == null) {
            return 500;
        }
        if (error instanceof ValidationError) {
            return 400;
        }
        if (error instanceof NotFoundError) {
            return 404;
        }
        if (error instanceof ConflictError) {
            return 409;
        }
        if (error instanceof UnprocessableEntityError) {
            return 422;
        }
        if (error instanceof AuthenticationError) {
            return 401;
        }
        if (error instanceof AuthorizationError) {
            return 403;
        }
        return 500;
    }
}
