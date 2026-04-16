package com.bank.core.dto;

import com.bank.core.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private String sourceAccountNumber;
        private String targetAccountNumber;
        private BigDecimal amount;
        private Transaction.Type type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String sourceAccountNumber;
        private String targetAccountNumber;
        private BigDecimal amount;
        private Transaction.Type type;
        private LocalDateTime createdAt;
    }
}
