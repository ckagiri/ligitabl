package com.ligitabl.model.domain.service;

import java.util.UUID;

import com.ligitabl.model.auth.PublicId;

public interface PublicIdGenerator {
    PublicId generate(UUID uuid);
}
