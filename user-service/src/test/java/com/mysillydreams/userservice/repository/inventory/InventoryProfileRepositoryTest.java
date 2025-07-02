package com.mysillydreams.userservice.repository.inventory;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException; // For unique constraint test
import org.springframework.test.context.ContextConfiguration;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest // Focuses on JPA components
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class) // Uses Testcontainers from base
// No CryptoConverter needed here as InventoryProfile fields are not encrypted by default
public class InventoryProfileRepositoryTest {

    @Autowired
    private InventoryProfileRepository inventoryProfileRepository;

    @Autowired
    private UserRepository userRepository; // To create associated UserEntity

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        // Clean up to ensure test isolation
        inventoryProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setReferenceId("inv-prof-user-" + UUID.randomUUID().toString());
        testUser.setEmail(testUser.getReferenceId() + "@example.com"); // Unique email
        testUser.setName("Inventory Test User");
        testUser = userRepository.saveAndFlush(testUser);
    }

    @AfterEach
    void tearDown() {
        inventoryProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    @Test
    void saveAndFindById_shouldPersistAndRetrieveProfile() {
        InventoryProfile profile = new InventoryProfile();
        profile.setUser(testUser);
        // No other mandatory fields in InventoryProfile currently other than user link

        InventoryProfile savedProfile = inventoryProfileRepository.saveAndFlush(profile);

        Optional<InventoryProfile> foundProfileOpt = inventoryProfileRepository.findById(savedProfile.getId());

        assertThat(foundProfileOpt).isPresent();
        InventoryProfile foundProfile = foundProfileOpt.get();
        assertThat(foundProfile.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(foundProfile.getCreatedAt()).isNotNull();
        assertThat(foundProfile.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByUser_whenProfileExists_returnsProfile() {
        InventoryProfile profile = new InventoryProfile();
        profile.setUser(testUser);
        inventoryProfileRepository.saveAndFlush(profile);

        Optional<InventoryProfile> foundProfileOpt = inventoryProfileRepository.findByUser(testUser);

        assertThat(foundProfileOpt).isPresent();
        assertThat(foundProfileOpt.get().getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    void findByUser_whenProfileDoesNotExist_returnsEmpty() {
        Optional<InventoryProfile> foundProfileOpt = inventoryProfileRepository.findByUser(testUser);
        assertThat(foundProfileOpt).isNotPresent();
    }

    @Test
    void findByUserId_whenProfileExists_returnsProfile() {
        InventoryProfile profile = new InventoryProfile();
        profile.setUser(testUser);
        inventoryProfileRepository.saveAndFlush(profile);

        Optional<InventoryProfile> foundProfileOpt = inventoryProfileRepository.findByUserId(testUser.getId());

        assertThat(foundProfileOpt).isPresent();
        assertThat(foundProfileOpt.get().getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    void findByUserId_whenProfileDoesNotExist_returnsEmpty() {
        Optional<InventoryProfile> foundProfileOpt = inventoryProfileRepository.findByUserId(testUser.getId());
        assertThat(foundProfileOpt).isNotPresent();
    }

    @Test
    void ensureOneToOneUserConstraint_onInventoryProfile() {
        // Create first profile
        InventoryProfile profile1 = new InventoryProfile();
        profile1.setUser(testUser);
        inventoryProfileRepository.saveAndFlush(profile1);

        // Attempt to create a second profile for the same user
        InventoryProfile profile2 = new InventoryProfile();
        profile2.setUser(testUser); // Same user

        // Expect a DataIntegrityViolationException due to unique constraint on user_id
        assertThrows(DataIntegrityViolationException.class, () -> {
            inventoryProfileRepository.saveAndFlush(profile2);
        });
    }
}
