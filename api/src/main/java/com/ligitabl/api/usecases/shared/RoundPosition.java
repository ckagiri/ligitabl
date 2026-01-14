package com.ligitabl.api.usecases.shared;

import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.exceptions.UseCaseException;

public class RoundPosition {
    public static Integer parse(String position) {
        if (position == null || "current".equalsIgnoreCase(position)) {
            return null;
        }

        try {
            return Integer.parseInt(position);
        } catch (NumberFormatException e) {
            throw new UseCaseException(UseCaseErrors.validation("Invalid round position format"));
        }
    }
}
