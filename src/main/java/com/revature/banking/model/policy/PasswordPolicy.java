package com.revature.banking.model.policy;

import com.revature.banking.validation.Validation;

public final class PasswordPolicy {
    public void validate(char[] password, String username) {
        Validation.password(password, username);
    }
}
