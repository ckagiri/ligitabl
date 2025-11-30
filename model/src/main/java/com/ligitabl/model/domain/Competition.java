package com.ligitabl.model.domain;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Competition extends AbstractModel<UUID> {
    @NotNull
    private String name;

    @NotBlank
    private CompetitionSlug slug;

    @NotNull
    private String code;
}
