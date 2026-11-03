package com.ligitabl.api.usecase.prediction.autojoin;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Runs one user's join inside the batch's transaction, so a non-database failure undoes only that
 * user's writes. A database error has already aborted the transaction: it's left that way, so the
 * whole batch rolls back and retries.
 */
@Component
class UserJoinSavepoint {

    <T> T call(Supplier<T> join) {
        var transaction = TransactionAspectSupport.currentTransactionStatus();
        Object savepoint = transaction.createSavepoint();
        try {
            T result = join.get();
            transaction.releaseSavepoint(savepoint);
            return result;
        } catch (org.jooq.exception.DataAccessException | org.springframework.dao.DataAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            transaction.rollbackToSavepoint(savepoint);
            throw e;
        }
    }
}
