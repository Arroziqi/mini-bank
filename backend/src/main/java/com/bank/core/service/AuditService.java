package com.bank.core.service;

import com.bank.core.model.AuditLog;
import com.bank.core.model.User;
import com.bank.core.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String details, User user) {
        AuditLog log = AuditLog.builder()
                .action(action)
                .details(details)
                .user(user)
                .build();
        auditLogRepository.save(log);
    }
}
