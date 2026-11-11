package com.revature.banking.util;

public interface IdentifierGenerator {
    String nextAccountNumber();

    String nextTransactionReference();
}
