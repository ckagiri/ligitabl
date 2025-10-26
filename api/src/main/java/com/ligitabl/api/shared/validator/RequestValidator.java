package com.ligitabl.api.shared.validator;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.errors.ValidationMessage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RequestValidator {

    private final Validator validator;

    public <T> Either<UseCaseError, T> validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);

        if (!violations.isEmpty()) {
            List<ValidationMessage> messages = violations.stream()
                .map(v -> new ValidationMessage(
                    v.getPropertyPath().toString(),
                    v.getMessage()))
                .toList();

            return Either.left(new ValidationError(messages));
        }

        return Either.right(request);
    }
}
