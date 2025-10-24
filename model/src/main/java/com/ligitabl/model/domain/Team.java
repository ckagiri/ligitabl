package com.ligitabl.model.domain;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team extends AbstractModel<UUID> {
    @NotNull
    private String name;
    @NotNull
    private String shortName;
}
