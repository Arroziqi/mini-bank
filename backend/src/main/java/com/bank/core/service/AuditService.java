package com.bank.core.service;

import com.bank.core.model.AuditLog;
import com.bank.core.model.User;
import com.bank.core.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String details, User user) {
        AuditLog auditLog = AuditLog.builder()
                .action(action)
                .details(details)
                .user(user)
                .build();
        auditLogRepository.save(auditLog);

        log.debug("Audit log created: action={}, user={}, details={}", action,
                user != null ? user.getUsername() : "system", details);
    }
}
