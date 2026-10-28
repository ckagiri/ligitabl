package com.ligitabl.api.usecase.auth.recordlogin;

import static org.mockito.Mockito.verify;

import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class RecordLoginUseCaseTest {

    @Mock
    UserRepo userRepo;

    @Test
    void stampsLastLoginFromTheClock() {
        UUID userId = UUID.randomUUID();

        new RecordLoginUseCase(userRepo, TestClock.FIXED).execute(userId);

        verify(userRepo).updateLastLoginAt(userId, TestClock.NOW.atOffset(ZoneOffset.UTC));
    }
}
