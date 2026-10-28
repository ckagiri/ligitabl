package com.ligitabl.api.usecase.auth.recordlogin;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

/** Stamps the user's last login. Every sign-in path calls this: form, Google, remember-me, impersonation. */
@Service
@RequiredArgsConstructor
public class RecordLoginUseCase {

    private final UserRepo userRepo;
    private final Clock clock;

    @Transactional
    public void execute(UUID userId) {
        userRepo.updateLastLoginAt(userId, OffsetDateTime.now(clock));
    }
}
