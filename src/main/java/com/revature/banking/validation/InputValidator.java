package com.revature.banking.validation;

public final class InputValidator {
    private InputValidator() {
    }

    public static boolean unsafeText(String value) {
        return value == null || value.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT);
    }
}
