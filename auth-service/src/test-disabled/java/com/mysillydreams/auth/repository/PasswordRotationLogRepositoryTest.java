package com.mysillydreams.auth.repository;

import com.mysillydreams.auth.config.PostgresTestContainerInitializer;
import com.mysillydreams.auth.domain.PasswordRotationLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
// Disable replacing the configured DataSource with an embedded H2 instance
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Use the Initializer to connect to the Testcontainer PostgreSQL
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
public class PasswordRotationLogRepositoryTest {

    @Autowired
    private PasswordRotationLogRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll(); // Clean up data after each test
    }

    @Test
    void saveAndFindById_shouldPersistAndRetrieveLog() {
        // Given
        UUID userId = UUID.randomUUID();
        Instant rotatedAt = Instant.now();
        PasswordRotationLog log = new PasswordRotationLog(userId, rotatedAt);

        // When
        PasswordRotationLog savedLog = repository.save(log);

        // Then
        assertThat(savedLog).isNotNull();
        assertThat(savedLog.getId()).isNotNull(); // ID should be generated

        Optional<PasswordRotationLog> foundLogOpt = repository.findById(savedLog.getId());
        assertThat(foundLogOpt).isPresent();
        PasswordRotationLog foundLog = foundLogOpt.get();
        assertThat(foundLog.getUserId()).isEqualTo(userId);
        // Instant comparison can be tricky due to precision. Truncate or check within a range if needed.
        // For basic check:
        assertThat(foundLog.getRotatedAt()).isEqualTo(rotatedAt);
    }

    @Test
    void findAll_shouldReturnAllSavedLogs() {
        // Given
        PasswordRotationLog log1 = new PasswordRotationLog(UUID.randomUUID(), Instant.now().minusSeconds(60));
        PasswordRotationLog log2 = new PasswordRotationLog(UUID.randomUUID(), Instant.now());
        repository.saveAll(List.of(log1, log2));

        // When
        List<PasswordRotationLog> logs = repository.findAll();

        // Then
        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(PasswordRotationLog::getUserId)
                       .containsExactlyInAnyOrder(log1.getUserId(), log2.getUserId());
    }

    @Test
    void deleteById_shouldRemoveLog() {
        // Given
        PasswordRotationLog log = new PasswordRotationLog(UUID.randomUUID(), Instant.now());
        PasswordRotationLog savedLog = repository.save(log);
        UUID savedLogId = savedLog.getId();

        // When
        repository.deleteById(savedLogId);

        // Then
        Optional<PasswordRotationLog> foundLogOpt = repository.findById(savedLogId);
        assertThat(foundLogOpt).isNotPresent();
    }

    // Example for a custom query method if it existed:
    /*
    @Test
    void findByUserIdOrderByRotatedAtDesc_shouldReturnLogsForUserInOrder() {
        // Given
        UUID targetUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        PasswordRotationLog log1UserTarget = new PasswordRotationLog(targetUserId, Instant.now().minusSeconds(120));
        PasswordRotationLog log2UserTarget = new PasswordRotationLog(targetUserId, Instant.now().minusSeconds(60));
        PasswordRotationLog log3UserTarget = new PasswordRotationLog(targetUserId, Instant.now());
        PasswordRotationLog logUserOther = new PasswordRotationLog(otherUserId, Instant.now());

        repository.saveAll(List.of(log1UserTarget, log2UserTarget, log3UserTarget, logUserOther));

        // When
        // Assuming a method: List<PasswordRotationLog> findByUserIdOrderByRotatedAtDesc(UUID userId);
        // List<PasswordRotationLog> userLogs = repository.findByUserIdOrderByRotatedAtDesc(targetUserId);

        // Then
        // assertThat(userLogs).hasSize(3);
        // assertThat(userLogs).extracting(PasswordRotationLog::getRotatedAt)
        //                    .containsExactly(log3UserTarget.getRotatedAt(), log2UserTarget.getRotatedAt(), log1UserTarget.getRotatedAt());
    }
    */
}
