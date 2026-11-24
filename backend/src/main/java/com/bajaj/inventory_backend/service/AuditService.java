package com.bajaj.inventory_backend.service;

import com.bajaj.inventory_backend.audit.AuditLog;
import com.bajaj.inventory_backend.audit.AuditRepository;
import com.bajaj.inventory_backend.entity.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional
    public void log(
            User user,
            String action,
            String entityType,
            Long entityId,
            String description) {

        AuditLog auditLog = new AuditLog();

        auditLog.setUser(user);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setDescription(description);

        auditRepository.save(auditLog);
    }
}