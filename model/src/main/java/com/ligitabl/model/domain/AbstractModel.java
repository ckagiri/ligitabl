package com.ligitabl.model.domain;

import com.ligitabl.model.shared.Identifiable;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder // Add equality logic based on 'id'
@EqualsAndHashCode(of = "id")
public abstract class AbstractModel<ID> implements Identifiable<ID> {
    protected ID id;
}
