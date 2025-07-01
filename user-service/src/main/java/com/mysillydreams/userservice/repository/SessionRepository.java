package com.mysillydreams.userservice.repository;

import com.mysillydreams.userservice.domain.SessionEntity;
import com.mysillydreams.userservice.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    /**
     * Finds all sessions associated with a given user, ordered by login time descending.
     *
     * @param user The user entity whose sessions are to be retrieved.
     * @return A list of {@link SessionEntity} belonging to the user, ordered by login time.
     */
    List<SessionEntity> findByUserOrderByLoginTimeDesc(UserEntity user);

    /**
     * Finds all sessions associated with a given user's ID, ordered by login time descending.
     *
     * @param userId The UUID of the user.
     * @return A list of {@link SessionEntity} belonging to the user with the given ID.
     */
    List<SessionEntity> findByUserIdOrderByLoginTimeDesc(UUID userId);

    /**
     * Finds sessions that were logged in before a certain time and have no logout time (still active).
     * Useful for cleanup tasks or identifying stale sessions.
     *
     * @param olderThan The timestamp to compare against.
     * @return A list of active sessions that started before the given timestamp.
     */
    List<SessionEntity> findByLoginTimeBeforeAndLogoutTimeIsNull(Instant olderThan);
}
