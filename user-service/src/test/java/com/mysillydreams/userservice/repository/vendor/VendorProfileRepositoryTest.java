package com.mysillydreams.userservice.repository.vendor;

import com.mysillydreams.userservice.config.UserIntegrationTestBase; // Using the same base for DB
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.domain.vendor.VendorStatus;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;


import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Using @SpringBootTest to get full context including UserIntegrationTestBase for PostgreSQL Testcontainer
// @DataJpaTest might be too restrictive if we need other beans or full entity lifecycle with converters.
// However, VendorProfile does not use CryptoConverter for its direct fields yet.
// Let's try with @DataJpaTest first, importing necessary components.
@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class) // For Testcontainers PostgreSQL
// If CryptoConverter was used in VendorProfile (e.g. for legalName), we'd need to import it and mock EncryptionService
// or use @SpringBootTest. For now, VendorProfile fields are plain.
// @Import(CryptoConverter.class)
public class VendorProfileRepositoryTest {

    @Autowired
    private VendorProfileRepository vendorProfileRepository;

    @Autowired
    private UserRepository userRepository; // To create a UserEntity first

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        // Clean up before each test to ensure isolation
        vendorProfileRepository.deleteAll();
        userRepository.deleteAll(); // Also delete users to avoid FK constraints if tests run in specific order

        testUser = new UserEntity();
        testUser.setReferenceId(UUID.randomUUID().toString());
        testUser.setEmail("vendor.user." + UUID.randomUUID() + "@example.com"); // Ensure unique email
        testUser.setName("Test Vendor User");
        testUser = userRepository.saveAndFlush(testUser); // Save user first
    }

    @AfterEach
    void tearDown() {
        vendorProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    @Test
    void saveAndFindById_shouldPersistAndRetrieveProfile() {
        VendorProfile profile = new VendorProfile();
        profile.setUser(testUser);
        profile.setLegalName("MegaCorp Vendor");
        profile.setStatus(VendorStatus.REGISTERED);
        profile.setKycWorkflowId("wf-initial");

        VendorProfile savedProfile = vendorProfileRepository.saveAndFlush(profile);

        Optional<VendorProfile> foundProfileOpt = vendorProfileRepository.findById(savedProfile.getId());

        assertThat(foundProfileOpt).isPresent();
        VendorProfile foundProfile = foundProfileOpt.get();
        assertThat(foundProfile.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(foundProfile.getLegalName()).isEqualTo("MegaCorp Vendor");
        assertThat(foundProfile.getStatus()).isEqualTo(VendorStatus.REGISTERED);
        assertThat(foundProfile.getKycWorkflowId()).isEqualTo("wf-initial");
        assertThat(foundProfile.getCreatedAt()).isNotNull();
        assertThat(foundProfile.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByUser_whenProfileExists_returnsProfile() {
        VendorProfile profile = new VendorProfile();
        profile.setUser(testUser);
        profile.setLegalName("Another Vendor Ltd");
        vendorProfileRepository.saveAndFlush(profile);

        Optional<VendorProfile> foundProfileOpt = vendorProfileRepository.findByUser(testUser);

        assertThat(foundProfileOpt).isPresent();
        assertThat(foundProfileOpt.get().getLegalName()).isEqualTo("Another Vendor Ltd");
    }

    @Test
    void findByUser_whenProfileDoesNotExist_returnsEmpty() {
        Optional<VendorProfile> foundProfileOpt = vendorProfileRepository.findByUser(testUser);
        assertThat(foundProfileOpt).isNotPresent();
    }

    @Test
    void findByUserId_whenProfileExists_returnsProfile() {
        VendorProfile profile = new VendorProfile();
        profile.setUser(testUser);
        profile.setLegalName("User ID Test Vendor");
        vendorProfileRepository.saveAndFlush(profile);

        Optional<VendorProfile> foundProfileOpt = vendorProfileRepository.findByUserId(testUser.getId());

        assertThat(foundProfileOpt).isPresent();
        assertThat(foundProfileOpt.get().getLegalName()).isEqualTo("User ID Test Vendor");
    }

    @Test
    void findByUserId_whenProfileDoesNotExist_returnsEmpty() {
        Optional<VendorProfile> foundProfileOpt = vendorProfileRepository.findByUserId(testUser.getId());
        assertThat(foundProfileOpt).isNotPresent();
    }

    @Test
    void ensureOneToOneUserConstraint() {
        // Create first profile
        VendorProfile profile1 = new VendorProfile();
        profile1.setUser(testUser);
        profile1.setLegalName("Vendor Profile 1");
        vendorProfileRepository.saveAndFlush(profile1);

        // Attempt to create a second profile for the same user
        VendorProfile profile2 = new VendorProfile();
        profile2.setUser(testUser); // Same user
        profile2.setLegalName("Vendor Profile 2");

        // Expect a DataIntegrityViolationException or similar due to unique constraint on user_id
        // The @JoinColumn(name="user_id", unique=true) in VendorProfile should enforce this.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            vendorProfileRepository.saveAndFlush(profile2);
        });
    }
}
