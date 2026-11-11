package com.revature.banking.model.policy;

import com.revature.banking.model.Account;
import com.revature.banking.exception.*;

public final class TransferPolicy {
    public void validate(Account source, Account destination) {
        if (source.id() == destination.id())
            throw new ValidationException("SAME_ACCOUNT", "Choose two different accounts.");
        source.requireCash();
        destination.requireCash();
    }
}
