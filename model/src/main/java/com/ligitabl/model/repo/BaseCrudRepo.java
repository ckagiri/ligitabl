package com.ligitabl.model.repo;

import java.util.Optional;

public interface BaseCrudRepo<T, ID> {
    Optional<T> findById(ID id);
    T create(T model);
    T update(T model);
    void delete(ID id);
}
