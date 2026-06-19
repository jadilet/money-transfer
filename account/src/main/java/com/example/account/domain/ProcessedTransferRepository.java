package com.example.account.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedTransferRepository extends JpaRepository<ProcessedTransfer, UUID> {
}
