package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Team extends AbstractModel<UUID> {
    @NotNull
    private String name;

    @NotNull
    private String shortName;

    // URL-friendly identifier (e.g., "manchester-united"), unique
    @NotBlank
    private TeamSlug slug;

    // Three-letter acronym (required, e.g., "MUN"); exactly 3 characters
    @NotNull
    @Size(min = 3, max = 3)
    private String tla;

    // Populated by the database (defaults/triggers)
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;
}
