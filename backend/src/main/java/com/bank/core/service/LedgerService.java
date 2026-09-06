package com.bank.core.service;

import com.bank.core.model.Account;
import com.bank.core.model.LedgerEntry;
import com.bank.core.model.LedgerTransaction;
import com.bank.core.model.Transaction;
import com.bank.core.repository.LedgerEntryRepository;
import com.bank.core.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public LedgerTransaction createLedger(Transaction transaction, Account source, Account target, BigDecimal amount) {
        log.debug("Creating ledger for transaction: id={}, source={}, target={}, amount={}",
                transaction.getId(), source.getAccountNumber(), target.getAccountNumber(), amount);

        LedgerTransaction ledgerTransaction = LedgerTransaction.builder()
                .transaction(transaction)
                .description("Transfer " + amount + " from " + source.getAccountNumber() + " to " + target.getAccountNumber())
                .build();
        ledgerTransaction = ledgerTransactionRepository.save(ledgerTransaction);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .ledgerTransaction(ledgerTransaction)
                .account(source)
                .entryType(LedgerEntry.EntryType.DEBIT)
                .amount(amount)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .ledgerTransaction(ledgerTransaction)
                .account(target)
                .entryType(LedgerEntry.EntryType.CREDIT)
                .amount(amount)
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        log.info("Ledger created: ledgerTxId={}, debit={}, credit={}", ledgerTransaction.getId(), amount, amount);
        return ledgerTransaction;
    }

    @Transactional
    public LedgerTransaction createSingleEntry(Transaction transaction, Account account, LedgerEntry.EntryType entryType, BigDecimal amount) {
        log.debug("Creating single ledger entry: txId={}, account={}, type={}, amount={}",
                transaction.getId(), account.getAccountNumber(), entryType, amount);

        LedgerTransaction ledgerTransaction = LedgerTransaction.builder()
                .transaction(transaction)
                .description(entryType + " " + amount + " " + account.getAccountNumber())
                .build();
        ledgerTransaction = ledgerTransactionRepository.save(ledgerTransaction);

        LedgerEntry entry = LedgerEntry.builder()
                .ledgerTransaction(ledgerTransaction)
                .account(account)
                .entryType(entryType)
                .amount(amount)
                .build();

        ledgerEntryRepository.save(entry);

        log.info("Single ledger entry created: ledgerTxId={}, type={}, amount={}", ledgerTransaction.getId(), entryType, amount);
        return ledgerTransaction;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateBalance(Account account) {
        BigDecimal totalCredit = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(
                account.getId(), LedgerEntry.EntryType.CREDIT);
        BigDecimal totalDebit = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(
                account.getId(), LedgerEntry.EntryType.DEBIT);

        BigDecimal calculatedBalance = totalCredit.subtract(totalDebit);

        log.info("Balance validation for account {}: stored={}, calculated={}, match={}",
                account.getAccountNumber(), account.getBalance(), calculatedBalance,
                account.getBalance().compareTo(calculatedBalance) == 0);

        return calculatedBalance;
    }
}
