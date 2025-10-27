package com.ligitabl.api.usecases.team;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode // equality based on these fields
@NoArgsConstructor(force = true) // allows frameworks like Jackson to deserialize
public class TeamPayload {

    @NotNull(message = "Team name is required")
    private String name;

    @NotNull(message = "Team short name is required")
    private String shortName;

    @NotNull(message = "Team slug is required")
    private String slug;

    @NotNull(message = "TLA is required")
    @Size(min = 3, max = 3, message = "TLA must be exactly 3 characters")
    private String tla;
}
