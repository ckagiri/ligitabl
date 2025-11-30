package com.ligitabl.model.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Value;

@Value(staticConstructor = "of")
public class CompetitionSlug {
    @NotBlank
    String value;
}
