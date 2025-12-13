package com.ligitabl.seed.internal;

import org.jooq.DSLContext;

/**
 * Base class for all seeders providing common functionality.
 * Handles result tracking and provides template method for seeding.
 */
public abstract class AbstractSeeder<T> {

    protected final DSLContext dsl;
    protected int inserted = 0;
    protected int skipped = 0;

    protected AbstractSeeder(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Template method for seeding. Subclasses implement the actual seeding logic.
     */
    public final SeedResult seed(T config) {
        if (!isValidConfig(config)) {
            return emptyResult();
        }

        resetCounters();
        performSeed(config);
        return createResult();
    }

    /**
     * Validates the configuration before seeding.
     */
    protected abstract boolean isValidConfig(T config);

    /**
     * Performs the actual seeding logic.
     */
    protected abstract void performSeed(T config);

    /**
     * Returns the name of this seeder for result reporting.
     */
    protected abstract String getSeederName();

    /**
     * Increments the inserted counter.
     */
    protected void recordInsert() {
        inserted++;
    }

    /**
     * Increments the skipped counter.
     */
    protected void recordSkip() {
        skipped++;
    }

    private void resetCounters() {
        inserted = 0;
        skipped = 0;
    }

    private SeedResult createResult() {
        return new SeedResult(getSeederName(), inserted, skipped);
    }

    private SeedResult emptyResult() {
        return new SeedResult(getSeederName(), 0, 0);
    }
}
