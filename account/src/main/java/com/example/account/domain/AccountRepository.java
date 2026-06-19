package com.example.account.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByClientId(UUID clientId);

    /**
     * Read an account for update with a pessimistic write lock ({@code SELECT ... FOR UPDATE}). The
     * money-move locks both accounts this way (in a deterministic order) so concurrent transfers on
     * the same account serialize instead of racing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockById(UUID id);
}
