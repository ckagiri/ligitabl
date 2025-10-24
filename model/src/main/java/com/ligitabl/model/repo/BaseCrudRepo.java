package com.ligitabl.model.repo;

import java.util.List;

public interface BaseCrudRepo<T, ID> {
    T findById(ID id);
    List<T> findAll();
    T create(T model);
    T update(T model);
    void delete(ID id);
}
