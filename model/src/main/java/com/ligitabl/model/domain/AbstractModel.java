package com.ligitabl.model.domain;

import com.ligitabl.model.shared.Identifiable;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public abstract class AbstractModel<ID> implements Identifiable<ID> {
    protected ID id;

    /** Same entity when the class and id match. An unsaved (null-id) instance equals only itself. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return id != null && id.equals(((AbstractModel<?>) o).id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
