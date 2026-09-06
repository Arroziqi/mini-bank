package com.bank.core.service;

import com.bank.core.exception.InsufficientBalanceException;
import com.bank.core.exception.ResourceNotFoundException;
import com.bank.core.model.Account;
import com.bank.core.model.Transaction;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private TransactionService transactionService;

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .role(User.Role.CUSTOMER)
                .build();

        account = Account.builder()
                .id(1L)
                .accountNumber("ACC-123456")
                .balance(new BigDecimal("1000.00"))
                .status(Account.Status.ACTIVE)
                .user(user)
                .version(0)
                .build();
    }

    @Test
    void deposit_shouldIncreaseBalance() {
        when(accountRepository.findByAccountNumberWithLock("ACC-123456")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        transactionService.deposit("ACC-123456", new BigDecimal("500.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("1500.00")) == 0
        ));
        verify(transactionRepository).save(any(Transaction.class));
        verify(auditService).log(eq("DEPOSIT"), anyString(), eq(user));
    }

    @Test
    void deposit_shouldThrowWhenAccountNotFound() {
        when(accountRepository.findByAccountNumberWithLock("ACC-NOTEXIST")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                transactionService.deposit("ACC-NOTEXIST", new BigDecimal("500.00"))
        );
    }

    @Test
    void withdraw_shouldDecreaseBalance() {
        when(accountRepository.findByAccountNumberWithLock("ACC-123456")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        transactionService.withdraw("ACC-123456", new BigDecimal("300.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("700.00")) == 0
        ));
        verify(transactionRepository).save(any(Transaction.class));
        verify(auditService).log(eq("WITHDRAWAL"), anyString(), eq(user));
    }

    @Test
    void withdraw_shouldThrowWhenInsufficientBalance() {
        when(accountRepository.findByAccountNumberWithLock("ACC-123456")).thenReturn(Optional.of(account));

        assertThrows(InsufficientBalanceException.class, () ->
                transactionService.withdraw("ACC-123456", new BigDecimal("5000.00"))
        );
    }

    @Test
    void withdraw_shouldThrowWhenAccountNotFound() {
        when(accountRepository.findByAccountNumberWithLock("ACC-NOTEXIST")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                transactionService.withdraw("ACC-NOTEXIST", new BigDecimal("100.00"))
        );
    }

    @Test
    void transfer_shouldMoveFundsBetweenAccounts() {
        Account target = Account.builder()
                .id(2L)
                .accountNumber("ACC-789012")
                .balance(new BigDecimal("500.00"))
                .status(Account.Status.ACTIVE)
                .user(user)
                .version(0)
                .build();

        when(accountRepository.findByAccountNumberWithLock("ACC-123456")).thenReturn(Optional.of(account));
        when(accountRepository.findByAccountNumberWithLock("ACC-789012")).thenReturn(Optional.of(target));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        transactionService.transfer("ACC-123456", "ACC-789012", new BigDecimal("200.00"), null, "Test transfer");

        verify(accountRepository, times(2)).save(any(Account.class));
        verify(transactionRepository).save(argThat(tx ->
                tx.getType() == Transaction.Type.TRANSFER
        ));
        verify(auditService).log(eq("TRANSFER"), anyString(), eq(user));
    }

    @Test
    void transfer_shouldThrowWhenSourceInsufficient() {
        when(accountRepository.findByAccountNumberWithLock("ACC-123456")).thenReturn(Optional.of(account));

        assertThrows(InsufficientBalanceException.class, () ->
                transactionService.transfer("ACC-123456", "ACC-789012", new BigDecimal("5000.00"), null, null)
        );
    }

    @Test
    void transfer_shouldThrowWhenSourceNotFound() {
        when(accountRepository.findByAccountNumberWithLock("ACC-NOTEXIST")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                transactionService.transfer("ACC-NOTEXIST", "ACC-789012", new BigDecimal("100.00"), null, null)
        );
    }
}
