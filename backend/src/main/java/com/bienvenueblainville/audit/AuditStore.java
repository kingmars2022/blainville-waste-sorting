package com.bienvenueblainville.audit;

import java.util.List;

/**
 * Where the audit trail lives.
 *
 * <p>An interface with two implementations, because "why MongoDB?" deserves a
 * better answer than "because the résumé says so". The honest position is that
 * for <em>this</em> workload MySQL's native JSON column does the job: the
 * volume is tiny, the reads are by entity, and the snapshots — while
 * differently shaped per entity type — are only ever read back whole.
 *
 * <p>What would actually justify the document store is querying <em>into</em>
 * those snapshots across heterogeneous shapes ("every change where the French
 * title mentioned a storm"), retention measured in years rather than rows, or
 * a write rate that makes an append-only collection with its own lifecycle
 * attractive. None of those are true here yet.
 *
 * <p>So both are implemented and the default is chosen by configuration. That
 * is not fence-sitting: it makes the claim testable. The same test suite runs
 * against either, and the difference in what the code looks like is the
 * argument.
 */
public interface AuditStore {
    /**
     * @implNote must be idempotent on {@code eventId} — delivery is
     *           at-least-once, and an audit trail that counts a change twice
     *           is not an audit trail.
     */
    void record(AuditRecord entry);

    List<AuditRecord> findByEntity(String entityType, Long entityId);

    long count();

    /** Reported on the admin endpoint so the answer to "where is this stored" is not a guess. */
    String backendName();
}
