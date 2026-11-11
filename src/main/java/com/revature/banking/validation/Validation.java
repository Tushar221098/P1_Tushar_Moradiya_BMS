package com.revature.banking.validation;

import com.revature.banking.exception.BankingException;

import java.math.BigDecimal;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Validation {
    public static final String PASSWORD_REQUIREMENTS =
            "Password must contain at least 10 characters, including an uppercase letter, "
                    + "a lowercase letter, a digit, and a non-whitespace symbol. "
                    + "It must be at most 72 UTF-8 bytes and must not equal your username (ignoring case).";

    private Validation() {
    }

    public static BigDecimal amount(String input, BigDecimal max) {
        if (input == null || !input.matches("[0-9]{1,17}(\\.[0-9]{1,2})?")) {
            throw BankingException.of("INVALID_AMOUNT", "Enter a positive amount with up to two decimal places.");
        }
        return amount(new BigDecimal(input), max);
    }

    public static BigDecimal amount(BigDecimal amount, BigDecimal max) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.compareTo(max) > 0) {
            throw BankingException.of("INVALID_AMOUNT", "Amount must be positive, at most " + max + ", with up to two decimal places.");
        }
        return amount.setScale(2);
    }

    public static boolean bcryptLength(char[] password) {
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        try {
            return bytes.remaining() <= 72;
        } finally {
            Arrays.fill(bytes.array(), (byte) 0);
        }
    }

    public static void password(char[] password, String username) {
        boolean upper = false, lower = false, digit = false, special = false;
        for (char ch : password) {
            upper |= Character.isUpperCase(ch);
            lower |= Character.isLowerCase(ch);
            digit |= Character.isDigit(ch);
            special |= !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch);
        }
        if (password.length < 10 || !bcryptLength(password) || !upper || !lower || !digit || !special
                || matchesUsername(password, username)) {
            throw BankingException.of("PASSWORD_POLICY",
                    PASSWORD_REQUIREMENTS);
        }
    }

    private static boolean matchesUsername(char[] password, String username) {
        if (password.length != username.length()) return false;
        for (int i = 0; i < password.length; i++) {
            if (Character.toLowerCase(password[i]) != Character.toLowerCase(username.charAt(i))) return false;
        }
        return true;
    }
}
