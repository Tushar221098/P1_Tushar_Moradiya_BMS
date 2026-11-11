package com.revature.banking.model.policy;

import com.revature.banking.model.*;

import java.time.LocalDate;

public final class AccountEligibilityPolicy {
    public boolean eligible(Account a, AccountUse use, LocalDate today, Money minimumDeposit) {
        return switch (use) {
            case ALL -> true;
            case CASH -> a.acceptsCashOperations();
            case DEPOSIT_FUNDING -> a.acceptsCashOperations() && a.money().compareTo(minimumDeposit) >= 0;
            case REPAYMENT -> a.acceptsRepayment();
            case SETTLEMENT -> a.canSettle(today);
        };
    }
}
