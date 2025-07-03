package com.mysillydreams.userservice.repository.delivery;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
// No CryptoConverter needed here as DeliveryProfile fields (vehicleDetails) are not encrypted by default.
public class DeliveryProfileRepositoryTest {

    @Autowired
    private DeliveryProfileRepository deliveryProfileRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        deliveryProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setReferenceId("del-prof-user-" + UUID.randomUUID());
        testUser.setEmail(testUser.getReferenceId() + "@example.com");
        testUser = userRepository.saveAndFlush(testUser);
    }

    @AfterEach
    void tearDown() {
        deliveryProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    @Test
    void saveAndFindById_shouldPersistAndRetrieveProfile() {
        DeliveryProfile profile = new DeliveryProfile();
        profile.setUser(testUser);
        profile.setVehicleDetails("Bike - Pulsar NS200");
        profile.setActive(true);

        DeliveryProfile savedProfile = deliveryProfileRepository.saveAndFlush(profile);
        Optional<DeliveryProfile> foundOpt = deliveryProfileRepository.findById(savedProfile.getId());

        assertThat(foundOpt).isPresent();
        DeliveryProfile found = foundOpt.get();
        assertThat(found.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(found.getVehicleDetails()).isEqualTo("Bike - Pulsar NS200");
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByUser_whenProfileExists_returnsProfile() {
        DeliveryProfile profile = new DeliveryProfile();
        profile.setUser(testUser);
        profile.setVehicleDetails("Scooter");
        deliveryProfileRepository.saveAndFlush(profile);

        Optional<DeliveryProfile> foundOpt = deliveryProfileRepository.findByUser(testUser);
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getVehicleDetails()).isEqualTo("Scooter");
    }

    @Test
    void findByUserId_whenProfileExists_returnsProfile() {
        DeliveryProfile profile = new DeliveryProfile();
        profile.setUser(testUser);
        deliveryProfileRepository.saveAndFlush(profile);

        Optional<DeliveryProfile> foundOpt = deliveryProfileRepository.findByUserId(testUser.getId());
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    void uniqueConstraintOnUser_shouldPreventMultipleProfilesForSameUser() {
        DeliveryProfile profile1 = new DeliveryProfile();
        profile1.setUser(testUser);
        profile1.setVehicleDetails("Vehicle 1");
        deliveryProfileRepository.saveAndFlush(profile1);

        DeliveryProfile profile2 = new DeliveryProfile();
        profile2.setUser(testUser); // Same user
        profile2.setVehicleDetails("Vehicle 2");

        assertThrows(DataIntegrityViolationException.class, () -> {
            deliveryProfileRepository.saveAndFlush(profile2);
        });
    }
}
