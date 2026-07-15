package com.ligitabl.api.auth;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ligitabl.api.auth.impersonation.CurrentUserFacade;
import com.ligitabl.api.auth.impersonation.UserSummary;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrentUserPublicId {
    private final UserRepo userRepo;
    private final CurrentUserFacade currentUserFacade;

    public Optional<PublicId> resolve() {
        // Data flows follow the effective user while an admin is impersonating
        if (currentUserFacade.isImpersonating()) {
            return currentUserFacade
                    .getEffectiveUser()
                    .map(UserSummary::publicId)
                    .map(PublicId::create);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            return Optional.empty();
        }

        try {
            Email email = Email.create(authentication.getName());
            return userRepo.findByEmail(email).map(User::getPublicId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
