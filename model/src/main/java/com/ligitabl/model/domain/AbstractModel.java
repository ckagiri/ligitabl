package com.ligitabl.model.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractModel<ID> {
    @NotNull
    protected ID id;
}
