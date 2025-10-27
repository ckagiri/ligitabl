package com.ligitabl.model.validator;

import java.lang.annotation.*;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = SlugValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSlug {
    String message() default "Invalid slug format. Must be lowercase alphanumeric with dashes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
