package com.ligitabl.api.shared;

/**
 * Unit type - represents the presence of a value without any meaningful data.
 * Used in functional programming when you need to return something but the value doesn't matter.
 *
 * Common use cases:
 * - Representing successful completion of operations that don't return a value
 * - Combining multiple validations where you only care about pass/fail
 * - Side-effect operations wrapped in Either/Try
 */
public final class Unit {

    /**
     * The single instance of Unit.
     */
    public static final Unit INSTANCE = new Unit();

    /**
     * Private constructor to prevent instantiation.
     * Use Unit.INSTANCE instead.
     */
    private Unit() {}

    @Override
    public String toString() {
        return "()";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Unit;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
