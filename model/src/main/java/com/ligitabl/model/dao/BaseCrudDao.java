package com.ligitabl.model.dao;

public interface BaseCrudDao<T, ID> {
    T findById(ID id);
    T create(T model);
    T update(T model);
    void delete(ID id);
}
