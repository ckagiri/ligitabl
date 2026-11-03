package com.ligitabl.model.validator;

import com.ligitabl.model.shared.InvalidValueException;

/**
 * Checks on values from outside the system, for value objects to use. A failure is shown to the user, so a
 * guard against a bug throws a plain JDK exception instead.
 */
public class AssertionUtils {
    public static void assertArgumentEquals(Object anObject1, Object anObject2, String aMessage) {
        if (!anObject1.equals(anObject2)) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentFalse(boolean aBoolean, String aMessage) {
        if (aBoolean) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentLength(String aString, int aMaximum, String aMessage) {
        int length = aString.trim().length();
        if (length > aMaximum) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentLength(String aString, int aMinimum, int aMaximum, String aMessage) {
        int length = aString.trim().length();
        if (length < aMinimum || length > aMaximum) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentNotEmpty(String aString, String aMessage) {
        if (aString == null || aString.isBlank()) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentNotEquals(Object anObject1, Object anObject2, String aMessage) {
        if (anObject1.equals(anObject2)) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentNotNull(Object anObject, String aMessage) {
        if (anObject == null) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentRange(double aValue, double aMinimum, double aMaximum, String aMessage) {
        if (aValue < aMinimum || aValue > aMaximum) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentRange(float aValue, float aMinimum, float aMaximum, String aMessage) {
        if (aValue < aMinimum || aValue > aMaximum) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentRange(int aValue, int aMinimum, int aMaximum, String aMessage) {
        if (aValue < aMinimum || aValue > aMaximum) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentRange(long aValue, long aMinimum, long aMaximum, String aMessage) {
        if (aValue < aMinimum || aValue > aMaximum) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertArgumentTrue(boolean aBoolean, String aMessage) {
        if (!aBoolean) {
            throw new InvalidValueException(aMessage);
        }
    }

    public static void assertStateFalse(boolean aBoolean, String aMessage) {
        if (aBoolean) {
            throw new IllegalStateException(aMessage);
        }
    }

    public static void assertStateTrue(boolean aBoolean, String aMessage) {
        if (!aBoolean) {
            throw new IllegalStateException(aMessage);
        }
    }
}
