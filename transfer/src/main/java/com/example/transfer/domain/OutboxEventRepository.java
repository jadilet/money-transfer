package com.example.transfer.domain;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Plain read (no lock) — for reporting/tests. */
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /**
     * Locking batch read for the relay: {@code SELECT ... FOR UPDATE SKIP LOCKED}. The lock hint
     * value {@code -2} is Hibernate's SKIP_LOCKED, so concurrent relays grab disjoint rows instead
     * of blocking. Must be called inside the relay's transaction; the lock is held only for the brief
     * publish-and-mark, then released on commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.status = :status order by e.createdAt asc")
    List<OutboxEvent> findBatchForPublishing(@Param("status") OutboxStatus status, Pageable pageable);
}
