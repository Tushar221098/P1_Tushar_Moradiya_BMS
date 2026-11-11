package com.revature.banking.util;

import java.util.UUID;
import java.security.SecureRandom;

import com.revature.banking.model.AccountNumber;

public final class ReferenceGenerator implements IdentifierGenerator {
    private final SecureRandom random = new SecureRandom();

    public String nextAccountNumber() {
        String prefix = Long.toString(random.nextLong(10_000_000_000L, 100_000_000_000L));
        for (int digit = 0; digit <= 9; digit++) {
            String number = prefix + digit;
            if (AccountNumber.validChecksum(number)) return new AccountNumber(number).value();
        }
        throw new IllegalStateException("Could not generate account checksum");
    }

    public String nextTransactionReference() {
        return "TX-" + UUID.randomUUID();
    }

    public static String accountNumber() {
        return new ReferenceGenerator().nextAccountNumber();
    }

    public static String transaction() {
        return new ReferenceGenerator().nextTransactionReference();
    }
}
