package com.ligitabl.api.rest.auth.getcurrentuser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetCurrentUserUseCase implements UseCase<GetCurrentUserQuery, Either<UseCaseError, UserInfo>> {
    private final UserRepo userRepo;

    @Override
    public Either<UseCaseError, UserInfo> execute(GetCurrentUserQuery query) {
        log.debug("Getting user info for public ID: {}", query.publicId());
        return Either.<UseCaseError, User>ofOptional(
                        userRepo.findByPublicId(query.publicId()),
                        () -> UseCaseErrors.notFound("User", query.publicId()))
                .map(user -> new UserInfo(
                        user.getPublicId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getRoles(),
                        user.isEmailVerified()));
    }
}
