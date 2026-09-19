package com.bienvenueblainville.audit;

import com.bienvenueblainville.events.OutboxMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Reading the audit trail back. Under {@code /api/admin/**}, so the ADMIN role
 * is already required.
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditController {
    private final AuditStore auditStore;
    private final OutboxMapper outbox;

    public AdminAuditController(AuditStore auditStore, OutboxMapper outbox) {
        this.auditStore = auditStore;
        this.outbox = outbox;
    }

    @GetMapping("/{entityType}/{entityId}")
    public List<AuditRecord> history(@PathVariable String entityType, @PathVariable Long entityId) {
        return auditStore.findByEntity(entityType, entityId);
    }

    /**
     * Operational truth about the pipeline rather than a liveness ping.
     *
     * <p>{@code pendingEvents} is the number that matters: the outbox commits
     * with the business write, so a figure that keeps climbing means the relay
     * or the broker has stopped while the application carries on accepting
     * changes perfectly well. That failure is otherwise silent.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "auditBackend", auditStore.backendName(),
                "auditRecords", auditStore.count(),
                "pendingEvents", outbox.countUnpublished());
    }
}
