package com.mysillydreams.userservice.repository;

import com.mysillydreams.userservice.domain.PaymentInfoEntity;
import com.mysillydreams.userservice.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentInfoRepository extends JpaRepository<PaymentInfoEntity, UUID> {

    /**
     * Finds all payment information records associated with a given user.
     *
     * @param user The user entity whose payment information is to be retrieved.
     * @return A list of {@link PaymentInfoEntity} belonging to the user.
     */
    List<PaymentInfoEntity> findByUser(UserEntity user);

    /**
     * Finds all payment information records associated with a given user's ID.
     *
     * @param userId The UUID of the user.
     * @return A list of {@link PaymentInfoEntity} belonging to the user with the given ID.
     */
    List<PaymentInfoEntity> findByUserId(UUID userId);

    // Optional: Find by card token if needed, though tokens should be unique per user.
    // Global uniqueness of card tokens might depend on the payment gateway's tokenization strategy.
    // Optional<PaymentInfoEntity> findByCardToken(String cardToken); // CardToken is encrypted
}
