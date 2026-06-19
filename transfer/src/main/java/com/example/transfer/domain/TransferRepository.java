package com.example.transfer.domain;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    /**
     * Locking work-list for the reconciler: PENDING transfers quiet since {@code updatedBefore},
     * read with {@code FOR UPDATE SKIP LOCKED} (hint {@code -2}). The reconciler claims rows in a
     * short transaction (locks, then touches them) so concurrent reconcilers take disjoint work.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select t from Transfer t where t.status = :status and t.updatedAt < :updatedBefore order by t.updatedAt asc")
    List<Transfer> findStuckForUpdate(@Param("status") TransferStatus status,
                                      @Param("updatedBefore") Instant updatedBefore,
                                      Pageable pageable);
}
