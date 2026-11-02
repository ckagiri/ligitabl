package com.ligitabl.api;

import static com.ligitabl.api.testsupport.ApiClasses.MAIN;
import static com.ligitabl.api.testsupport.ApiClasses.ROOT;
import static com.ligitabl.api.testsupport.ApiClasses.pkg;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.ligitabl.api.scheduling.EmailVerificationTokenCleanupJob;
import com.ligitabl.api.scheduling.PasswordResetTokenCleanupJob;
import com.ligitabl.api.scheduling.outbox.OutboxEventProcessor;
import com.ligitabl.api.scheduling.outbox.OutboxRelayJob;
import com.ligitabl.api.web.finaltable.dev.DevFinalTableController;
import com.ligitabl.model.repo.UserRepo;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;

/**
 * Adapters may read a repo to assemble a view, but every write goes through a use case (rule 2 in
 * docs/architecture.md). A repo method counts as a read only if its name starts with a read verb, so a
 * new verb counts as a write until it is added here.
 *
 * <p>Runners are out of scope: they are self-contained tools with their own use cases.
 */
class AdapterRepoWriteRuleTest {

    private static final String REPO = UserRepo.class.getPackageName();

    private static final Pattern READ = Pattern.compile("^(find|exists|count|sum|is|has|get)([A-Z].*)?$");

    /** Writes with no business rule to move: outbox delivery bookkeeping, token housekeeping, a dev tool. */
    private static final Map<Class<?>, Set<String>> EXEMPT = Map.of(
            OutboxRelayJob.class, Set.of("claimBatchForProcessing", "resetStuckProcessing"),
            OutboxEventProcessor.class, Set.of("markSent", "markFailed", "markDeferred", "markDeadLetter"),
            EmailVerificationTokenCleanupJob.class, Set.of("deleteExpired"),
            PasswordResetTokenCleanupJob.class, Set.of("deleteExpired"),
            DevFinalTableController.class, Set.of("clearResults"));

    private static final DescribedPredicate<JavaMethodCall> REPO_WRITE =
            DescribedPredicate.describe("a repo write", call -> {
                String method = call.getTarget().getName();
                return call.getTargetOwner().getPackageName().equals(REPO)
                        && !READ.matcher(method).matches()
                        && !isExempt(call.getOriginOwner().getName(), method);
            });

    @Test
    void adaptersDoNotWriteThroughRepositories() {
        noClasses()
                .that()
                .resideInAPackage(ROOT + "..")
                .and()
                .resideOutsideOfPackages(pkg("usecase"), pkg("runners"))
                .should()
                .callMethodWhere(REPO_WRITE)
                .because("a write goes through a use case; see docs/architecture.md")
                .check(MAIN);
    }

    private static boolean isExempt(String className, String method) {
        return EXEMPT.entrySet().stream()
                .anyMatch(e ->
                        e.getKey().getName().equals(className) && e.getValue().contains(method));
    }
}
