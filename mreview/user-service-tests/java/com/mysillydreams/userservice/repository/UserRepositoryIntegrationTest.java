package com.mysillydreams.userservice.repository;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.converter.CryptoConverter;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.service.EncryptionService; // Needed for @SpringBootTest to pick up
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;


import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Using @SpringBootTest to load full application context for proper CryptoConverter and EncryptionService wiring with Vault.
// @DataJpaTest might be too restrictive for testing with real encryption service.
@SpringBootTest
// We are using Testcontainers, so no need to replace the datasource.
// @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // Not needed with @SpringBootTest if props set by Initializer
public class UserRepositoryIntegrationTest extends UserIntegrationTestBase {

    @Autowired
    private EntityManager entityManager; // Using EntityManager for more control in setup/cleanup if needed

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionService encryptionService; // Real service connected to Testcontainer Vault

    @Autowired
    private CryptoConverter cryptoConverter; // Ensure it's managed and wired

    @BeforeEach
    void setUp() {
        // Ensure CryptoConverter has the real EncryptionService.
        // If CryptoConverter is a @Component and EncryptionService is a @Service,
        // Spring's DI should handle this. The static setter in CryptoConverter is key.
        // We can explicitly call the setter here to be absolutely sure for the test context,
        // though it should happen automatically if CryptoConverter is properly managed by Spring.
        cryptoConverter.setEncryptionService(encryptionService);
        userRepository.deleteAll(); // Clean before each test
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }


    @Test
    void findByReferenceId_whenUserExists_returnsUserWithDecryptedFields() {
        // Given
        UserEntity user = new UserEntity();
        user.setReferenceId("ref123-encrypted");
        String plainName = "Test User Encrypted";
        String plainEmail = "test.encrypted@example.com";
        user.setName(plainName); // Will be encrypted by CryptoConverter via EncryptionService
        user.setEmail(plainEmail); // Will be encrypted

        // Must save via repository to trigger converters
        UserEntity persistedUser = userRepository.saveAndFlush(user);
        entityManager.detach(persistedUser); // Detach to ensure fresh load from DB

        // When
        Optional<UserEntity> foundUserOpt = userRepository.findByReferenceId("ref123-encrypted");

        // Then
        assertThat(foundUserOpt).isPresent();
        UserEntity foundUser = foundUserOpt.get();
        // Fields should be decrypted by CryptoConverter on load
        assertThat(foundUser.getName()).isEqualTo(plainName);
        assertThat(foundUser.getEmail()).isEqualTo(plainEmail);

        // Verify that data in DB is actually encrypted (cannot be easily done without raw JDBC access here)
        // But if decryption works, encryption likely did too.
    }

    @Test
    void findByReferenceId_whenUserDoesNotExist_returnsEmpty() {
        Optional<UserEntity> foundUserOpt = userRepository.findByReferenceId("nonexistentref-encrypted");
        assertThat(foundUserOpt).isNotPresent();
    }

    @Test
    void findByEmail_whenUserExists_returnsUserWithDecryptedFields() {
        // Given
        UserEntity user = new UserEntity();
        String plainEmail = "findby.encrypted.email@example.com";
        String plainName = "Email User Encrypted";
        user.setReferenceId(UUID.randomUUID().toString());
        user.setName(plainName);
        user.setEmail(plainEmail); // Will be encrypted by CryptoConverter

        userRepository.saveAndFlush(user);
        entityManager.clear(); // Clear persistence context to ensure fresh load

        // When
        // To search by encrypted email, we must provide the encrypted form to the query method.
        String encryptedEmailToSearch = encryptionService.encrypt(plainEmail);
        Optional<UserEntity> foundUserOpt = userRepository.findByEmail(encryptedEmailToSearch);

        // Then
        assertThat(foundUserOpt).isPresent();
        UserEntity foundUser = foundUserOpt.get();
        assertThat(foundUser.getName()).isEqualTo(plainName); // Decrypted automatically
        assertThat(foundUser.getEmail()).isEqualTo(plainEmail); // Decrypted automatically
    }

    @Test
    void findByEmail_withPlainTextEmail_whenEncryptedInDb_shouldNotFind() {
        // Given
        UserEntity user = new UserEntity();
        String plainEmail = "plaintext.search.fail@example.com";
        user.setReferenceId(UUID.randomUUID().toString());
        user.setName("Plain Search User");
        user.setEmail(plainEmail); // Will be encrypted

        userRepository.saveAndFlush(user);
        entityManager.clear();

        // When
        // Searching with plain text email against an encrypted column (even if mock makes it plain)
        Optional<UserEntity> foundUserOpt = userRepository.findByEmail(plainEmail);

        // Then
        // This should NOT find the user because the value in DB is encrypted.
        // The findByEmail query compares its input with the value in the DB column.
        // If the input 'plainEmail' is not what's stored (i.e., the encrypted form), it won't match.
        assertThat(foundUserOpt).isNotPresent();
    }


    @Test
    void saveUser_shouldPersistUserWithEncryptedFields() {
        UserEntity user = new UserEntity();
        user.setReferenceId(UUID.randomUUID().toString());
        String plainName = "Persistent Encrypted User";
        String plainEmail = "persist.encrypted@example.com";
        user.setName(plainName);
        user.setEmail(plainEmail);
        user.setDob("2000-01-01"); // This will also be encrypted

        UserEntity savedUser = userRepository.saveAndFlush(user);
        entityManager.detach(savedUser); // Detach before reloading

        Optional<UserEntity> foundOpt = userRepository.findById(savedUser.getId());

        assertThat(foundOpt).isPresent();
        UserEntity foundUser = foundOpt.get();
        assertThat(foundUser.getName()).isEqualTo(plainName); // Decrypted
        assertThat(foundUser.getEmail()).isEqualTo(plainEmail); // Decrypted
        assertThat(foundUser.getDob()).isEqualTo("2000-01-01"); // Decrypted

        // Verify timestamps
        assertThat(foundUser.getCreatedAt()).isNotNull();
        assertThat(foundUser.getUpdatedAt()).isNotNull();
    }
}
