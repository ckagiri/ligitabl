package com.ligitabl.api.shared;

/** The result of an operation that succeeds without a value, e.g. {@code Either<Error, Unit>}. */
public final class Unit {

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
