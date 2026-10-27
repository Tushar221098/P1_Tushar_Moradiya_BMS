package com.revature.banking.security;

public interface PasswordHasher {
    String hash(char[] password);
    boolean verify(char[] password, String storeHash);
}
