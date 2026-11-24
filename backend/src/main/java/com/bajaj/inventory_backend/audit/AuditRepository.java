
package com.bajaj.inventory_backend.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AuditRepository
        extends JpaRepository<AuditLog, Long> {

    // Get all audit logs with pagination
    Page<AuditLog> findAllByOrderByCreatedAtDesc(
            Pageable pageable
    );

    // Find audit logs by user ID
    List<AuditLog> findByUser_IdOrderByCreatedAtDesc(
            Long userId
    );

    // Filter logs by action
    List<AuditLog> findByActionIgnoreCaseOrderByCreatedAtDesc(
            String action
    );

    // Find logs by entity type and entity ID
    List<AuditLog> findByEntityTypeIgnoreCaseAndEntityIdOrderByCreatedAtDesc(
            String entityType,
            Long entityId
    );

    // Find logs within a date range
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            Instant startDate,
            Instant endDate
    );
}
