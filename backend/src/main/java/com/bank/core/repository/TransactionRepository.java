package com.bank.core.repository;

import com.bank.core.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.id = :accountId OR t.targetAccount.id = :accountId")
    Page<Transaction> findAllByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount.id = :accountId OR t.targetAccount.id = :accountId) " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    Page<Transaction> findAllByAccountIdAndDateRange(@Param("accountId") Long accountId, 
                                                   @Param("startDate") LocalDateTime startDate, 
                                                   @Param("endDate") LocalDateTime endDate, 
                                                   Pageable pageable);
}
