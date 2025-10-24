package com.ligitabl.api.shared;

import org.springframework.stereotype.Component;

@Component
public class ModelValidator {
    public <T> T requireFound(T model) {
        if (model == null) {
            throw new RuntimeException("Requested model was not found");
        }
        return model;
    }
}
