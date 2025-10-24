package com.ligitabl.model.repo;

public interface BaseCrudRepo<T, ID> {
    T findById(ID id);
    T create(T model);
    T update(T model);
    void delete(ID id);
}
