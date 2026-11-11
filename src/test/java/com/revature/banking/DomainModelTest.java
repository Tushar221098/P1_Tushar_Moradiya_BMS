package com.revature.banking;

import com.revature.banking.model.*;
import com.revature.banking.exception.*;
import com.revature.banking.util.ReferenceGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {
    @Test
    void moneyRejectsInvalidScaleAndNegativeValues() {
        assertThrows(ValidationException.class, () -> new Money(new BigDecimal("1.001")));
        assertThrows(ValidationException.class, () -> new Money(new BigDecimal("-1")));
        assertThrows(ValidationException.class, () -> Money.ZERO.requirePositive());
        assertEquals(new BigDecimal("10.00"), new Money(BigDecimal.TEN).value());
        assertEquals(new Money(new BigDecimal("12.00")), new Money(BigDecimal.TEN).add(new Money(new BigDecimal("2"))));
    }

    @Test
    void onlyActiveCashAccountsAllowDebitsAndCredits() {
        Account cash = Account.restore(1, 1, "LEGACY1", AccountType.SAVINGS, BigDecimal.TEN, AccountStatus.ACTIVE, null);
        assertInstanceOf(SavingsAccount.class, cash);
        assertEquals(new BigDecimal("6.00"), cash.debit(new Money(new BigDecimal("4"))).value());
        assertThrows(InsufficientFundsException.class, () -> cash.debit(new Money(new BigDecimal("11"))));
        Account frozen = Account.restore(2, 1, "LEGACY2", AccountType.CURRENT, BigDecimal.TEN, AccountStatus.FROZEN, null);
        assertThrows(AccountNotEligibleException.class, () -> frozen.credit(new Money(BigDecimal.ONE)));
        assertEquals(BigDecimal.TEN.setScale(2), cash.balance());
    }

    @Test
    void termDepositRequiresMaturityAndCannotActAsCash() {
        LocalDate maturity = LocalDate.of(2026, 12, 1);
        Account deposit = Account.restore(1, 1, "LEGACY1", AccountType.DEPOSIT, BigDecimal.TEN, AccountStatus.ACTIVE, maturity);
        assertThrows(AccountNotEligibleException.class, () -> deposit.settle(maturity.minusDays(1)));
        assertEquals(new Money(BigDecimal.TEN), deposit.settle(maturity));
        assertThrows(AccountNotEligibleException.class, () -> deposit.debit(new Money(BigDecimal.ONE)));
    }

    @Test
    void loanRepaymentIsNotACashDeposit() {
        Account loan = Account.restore(1, 1, "LEGACY1", AccountType.LOAN, BigDecimal.TEN, AccountStatus.ACTIVE, null);
        assertEquals(Money.ZERO, loan.repay(new Money(BigDecimal.TEN)));
        assertThrows(ValidationException.class, () -> loan.repay(new Money(new BigDecimal("11"))));
        assertThrows(AccountNotEligibleException.class, () -> loan.credit(new Money(BigDecimal.ONE)));
    }

    @Test
    void newNumbersHaveChecksumsWhileLegacyNumbersStayReadable() {
        var generator = new ReferenceGenerator();
        var generated = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            String number = generator.nextAccountNumber();
            assertTrue(number.matches("[1-9][0-9]{11}"));
            assertTrue(AccountNumber.validChecksum(number));
            assertTrue(generated.add(number));
            assertEquals(number, new AccountNumber(number).value());
            String invalid = number.substring(0, 11) + ((number.charAt(11) - '0' + 1) % 10);
            assertThrows(ValidationException.class, () -> new AccountNumber(invalid));
        }
        assertEquals("DEMO1001", AccountNumber.persisted("DEMO1001").value());
        assertEquals("****1001", AccountNumber.persisted("DEMO1001").toString());
    }

    @Test
    void ledgerRejectsInvalidShapesAndReferences() {
        TransactionReference ref = new TransactionReference(ReferenceGenerator.transaction());
        assertThrows(ValidationException.class, () -> new BankTransaction(ref, TransactionType.TRANSFER,
                1L, 1L, new Money(BigDecimal.ONE), Money.ZERO, Money.ZERO, null));
        assertThrows(ValidationException.class, () -> new TransactionReference("user supplied"));
        BankTransaction entry = new BankTransaction(ref, TransactionType.DEPOSIT, null, 1L,
                new Money(BigDecimal.ONE), null, new Money(BigDecimal.ONE), null);
        assertEquals(TransactionStatus.SUCCESS, entry.status());
    }

    @Test
    void identityAndTermsValidateAtConstruction() {
        assertThrows(ValidationException.class, () -> new Username("x"));
        assertThrows(ValidationException.class, () -> new Email("bad email"));
        assertEquals("demo@example.test", new Email("Demo@Example.test").value());
        assertThrows(ValidationException.class, () -> new LoanTerms(new Money(BigDecimal.ONE), 0, LocalDate.now()));
        DepositTerms terms = DepositTerms.open(new Money(BigDecimal.TEN), 2, LocalDate.of(2026, 1, 1));
        assertEquals(LocalDate.of(2026, 3, 1), terms.maturityDate());
        String hash = new com.revature.banking.security.BcryptPasswordHasher(4).hash("Fake!Password123".toCharArray());
        assertFalse(new PasswordHash(hash).toString().contains(hash));
    }
}
