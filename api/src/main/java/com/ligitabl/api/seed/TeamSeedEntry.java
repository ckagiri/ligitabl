package com.ligitabl.api.seed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamSeedEntry {
    @NotBlank
    private String name;

    @NotBlank
    private String shortName;

    // URL-friendly unique identifier, e.g. "manchester-united"
    @NotBlank
    private String slug;

    // Three-letter acronym, e.g. "MUN"
    @NotBlank
    @Size(min = 3, max = 3)
    private String tla;
}
