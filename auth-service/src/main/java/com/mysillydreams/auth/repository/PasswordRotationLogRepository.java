package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.domain.PasswordRotationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PasswordRotationLogRepository extends JpaRepository<PasswordRotationLog, UUID> {

    // Example of a custom query method if needed in the future:
    // List<PasswordRotationLog> findByUserIdOrderByRotatedAtDesc(UUID userId);

}
