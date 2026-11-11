package com.revature.banking.security;

import at.favre.lib.crypto.bcrypt.BCrypt;

public final class BcryptPasswordHasher implements PasswordHasher {
    private final int cost;

    public BcryptPasswordHasher(int cost) {
        this.cost = cost;
    }

    @Override
    public String hash(char[] password) {
        return BCrypt.withDefaults().hashToString(cost, password);
    }

    @Override
    public boolean verify(char[] password, String storedHash) {
        return BCrypt.verifyer().verify(password, storedHash).verified;
    }
}
