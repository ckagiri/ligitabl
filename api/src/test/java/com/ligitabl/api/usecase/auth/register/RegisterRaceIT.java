package com.ligitabl.api.usecase.auth.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.model.domain.identity.Email;
import com.ligitabl.model.domain.identity.Password;
import com.ligitabl.model.repo.UserRepo;

@SpringBootTest
@DisplayName("Two signups racing for one email (real Postgres)")
class RegisterRaceIT extends AbstractPostgresIT {

    @Autowired
    RegisterUseCase registerUseCase;

    @SpyBean
    UserRepo userRepo;

    @Test
    @DisplayName("The loser gets a conflict once the unique constraint stops the insert")
    void losingTheRaceIsAConflict() {
        Email email = Email.create("race-" + UUID.randomUUID() + "@example.com");
        Password.Plaintext password = Password.Plaintext.create("testPassword123");
        assertThat(registerUseCase
                        .execute(new RegisterCommand(email, "First", password))
                        .isRight())
                .isTrue();

        // Both requests have passed the existence check by the time the second one inserts.
        doReturn(false).when(userRepo).existsByEmail(any());

        var result = registerUseCase.execute(new RegisterCommand(email, "Second", password));

        assertThat(result.getLeft()).isInstanceOf(ConflictError.class);
        assertThat(userRepo.findByEmail(email)).isPresent();
    }
}
