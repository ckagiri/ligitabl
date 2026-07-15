package com.ligitabl.api.auth;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.ligitabl.api.auth.impersonation.CurrentUserFacade;
import com.ligitabl.api.auth.impersonation.UserSummary;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrentUserId {
    private final UserRepo userRepo;
    private final CurrentUserFacade currentUserFacade;

    public UUID require() {
        // Data flows follow the effective user while an admin is impersonating
        if (currentUserFacade.isImpersonating()) {
            return currentUserFacade.getEffectiveUser().map(UserSummary::id).orElseThrow();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }

        PublicId publicId = PublicId.create(authentication.getName());

        return userRepo.findByPublicId(publicId)
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
