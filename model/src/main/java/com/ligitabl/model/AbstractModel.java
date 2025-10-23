package com.ligitabl.model;

import jakarta.validation.constraints.NotNull;

public abstract class AbstractModel<ID> {
    @NotNull
    protected ID id;
}
