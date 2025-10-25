package com.ligitabl.model.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder// Add equality logic based on 'id'
@EqualsAndHashCode(of = "id")
public abstract class AbstractModel<ID> {
    protected ID id;
}
