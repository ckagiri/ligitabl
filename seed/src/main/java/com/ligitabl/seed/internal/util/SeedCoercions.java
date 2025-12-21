package com.ligitabl.seed.internal.util;

public final class SeedCoercions {

    private SeedCoercions() {}

    public static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        throw new IllegalArgumentException("Expected string value but got: " + value);
    }

    public static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            return Integer.parseInt(s);
        }
        throw new IllegalArgumentException("Expected integer value but got: " + value);
    }

    public static int asInt(Object value) {
        Integer integer = asInteger(value);
        return integer != null ? integer : 0;
    }

    public static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            return Long.parseLong(s);
        }
        throw new IllegalArgumentException("Expected long value but got: " + value);
    }

    public static Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        throw new IllegalArgumentException("Expected boolean value but got: " + value);
    }
}
