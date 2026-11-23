
package com.bajaj.inventory_backend.controller;

import com.bajaj.inventory_backend.audit.AuditLog;
import com.bajaj.inventory_backend.audit.AuditRepository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditRepository auditRepository;

    public AuditController(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLogResponse> getAllAuditLogs(Pageable pageable) {
        return auditRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(log -> new AuditLogResponse(
                        log.getId(),
                        log.getUser() != null ? log.getUser().getId() : null,
                        log.getAction(),
                        log.getEntityType(),
                        log.getEntityId(),
                        log.getDescription(),
                        log.getCreatedAt()
                ));
    }

    public record AuditLogResponse(
            Long id,
            Long userId,
            String action,
            String entityType,
            Long entityId,
            String description,
            Instant createdAt
    ) {}
}
