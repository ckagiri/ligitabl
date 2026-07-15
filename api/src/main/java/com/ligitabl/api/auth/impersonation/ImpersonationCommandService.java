package com.ligitabl.api.auth.impersonation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImpersonationCommandService {

    private final UserRepo userRepo;
    private final ImpersonationAuthorizationService authorizationService;

    /**
     * @param originalUserId the real (admin) principal's user id
     * @param identifier email or username of the impersonation target
     */
    public ImpersonationAuthorizationService.Result start(
            UUID originalUserId, String identifier, HttpSession session) {
        User original = userRepo.findById(originalUserId).orElse(null);

        var result = authorizationService.assertCanImpersonate(original, identifier);
        if (result instanceof ImpersonationAuthorizationService.Result.Ok ok) {
            User target = ok.target();
            session.setAttribute(
                    ImpersonationSession.SESSION_ATTRIBUTE,
                    new ImpersonationSession(
                            target.getId(), target.getEmail().value(), Instant.now(), originalUserId));
            log.warn(
                    "[IMPERSONATION_START] admin={} target={} targetEmail={}",
                    originalUserId,
                    target.getId(),
                    target.getEmail().value());
        } else {
            log.warn("[IMPERSONATION_START_REJECTED] admin={} identifier={} result={}",
                    originalUserId, identifier, result.getClass().getSimpleName());
        }
        return result;
    }

    public void stop(HttpSession session) {
        Object existing = session.getAttribute(ImpersonationSession.SESSION_ATTRIBUTE);
        session.removeAttribute(ImpersonationSession.SESSION_ATTRIBUTE);
        if (existing instanceof ImpersonationSession impersonation) {
            log.warn(
                    "[IMPERSONATION_STOP] admin={} target={}",
                    impersonation.originalUserId(),
                    impersonation.targetUserId());
        }
    }
}
