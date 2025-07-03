package com.mysillydreams.userservice.repository.support;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.support.SupportProfile;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
public class SupportProfileRepositoryTest {

    @Autowired
    private SupportProfileRepository supportProfileRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser1;
    private UserEntity testUser2;

    @BeforeEach
    void setUp() {
        supportProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser1 = new UserEntity();
        testUser1.setReferenceId("support-user1-" + UUID.randomUUID());
        testUser1.setEmail(testUser1.getReferenceId() + "@example.com");
        testUser1 = userRepository.saveAndFlush(testUser1);

        testUser2 = new UserEntity();
        testUser2.setReferenceId("support-user2-" + UUID.randomUUID());
        testUser2.setEmail(testUser2.getReferenceId() + "@example.com");
        testUser2 = userRepository.saveAndFlush(testUser2);
    }

    @AfterEach
    void tearDown() {
        supportProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    @Test
    void saveAndFindById_shouldPersistAndRetrieveProfile() {
        SupportProfile profile = new SupportProfile();
        profile.setUser(testUser1);
        profile.setSpecialization("Billing");
        profile.setActive(true);

        SupportProfile savedProfile = supportProfileRepository.saveAndFlush(profile);
        Optional<SupportProfile> foundOpt = supportProfileRepository.findById(savedProfile.getId());

        assertThat(foundOpt).isPresent();
        SupportProfile found = foundOpt.get();
        assertThat(found.getUser().getId()).isEqualTo(testUser1.getId());
        assertThat(found.getSpecialization()).isEqualTo("Billing");
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByUser_whenProfileExists_returnsProfile() {
        SupportProfile profile = new SupportProfile();
        profile.setUser(testUser1);
        supportProfileRepository.saveAndFlush(profile);

        Optional<SupportProfile> foundOpt = supportProfileRepository.findByUser(testUser1);
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getUser().getId()).isEqualTo(testUser1.getId());
    }

    @Test
    void findByUserId_whenProfileExists_returnsProfile() {
        SupportProfile profile = new SupportProfile();
        profile.setUser(testUser1);
        supportProfileRepository.saveAndFlush(profile);

        Optional<SupportProfile> foundOpt = supportProfileRepository.findByUserId(testUser1.getId());
        assertThat(foundOpt).isPresent();
    }

    @Test
    void findByActiveTrue_returnsOnlyActiveProfiles() {
        SupportProfile activeProfile = new SupportProfile();
        activeProfile.setUser(testUser1);
        activeProfile.setActive(true);
        supportProfileRepository.save(activeProfile);

        SupportProfile inactiveProfile = new SupportProfile();
        inactiveProfile.setUser(testUser2);
        inactiveProfile.setActive(false);
        supportProfileRepository.save(inactiveProfile);
        supportProfileRepository.flush();


        List<SupportProfile> activeProfiles = supportProfileRepository.findByActiveTrue();
        assertThat(activeProfiles).hasSize(1);
        assertThat(activeProfiles.get(0).getUser().getId()).isEqualTo(testUser1.getId());
    }

    @Test
    void findByActiveTrueAndSpecializationIgnoreCase_returnsMatchingProfiles() {
        SupportProfile profile1 = new SupportProfile();
        profile1.setUser(testUser1);
        profile1.setSpecialization("Technical Support");
        profile1.setActive(true);
        supportProfileRepository.save(profile1);

        SupportProfile profile2 = new SupportProfile();
        profile2.setUser(testUser2);
        profile2.setSpecialization("TECHNICAL SUPPORT"); // Different case
        profile2.setActive(true);
        supportProfileRepository.save(profile2);

        SupportProfile profile3 = new SupportProfile(); // Different spec
        UserEntity user3 = userRepository.save(new UserEntity(){{setReferenceId("u3"); setEmail("u3@ex.com");}});
        profile3.setUser(user3);
        profile3.setSpecialization("Billing");
        profile3.setActive(true);
        supportProfileRepository.save(profile3);
        supportProfileRepository.flush();


        List<SupportProfile> techProfiles = supportProfileRepository.findByActiveTrueAndSpecializationIgnoreCase("technical support");
        assertThat(techProfiles).hasSize(2);
        assertThat(techProfiles).extracting(p -> p.getUser().getId()).containsExactlyInAnyOrder(testUser1.getId(), testUser2.getId());
    }

    @Test
    void uniqueConstraintOnUser_shouldPreventMultipleProfilesForSameUser() {
        SupportProfile profile1 = new SupportProfile();
        profile1.setUser(testUser1);
        supportProfileRepository.saveAndFlush(profile1);

        SupportProfile profile2 = new SupportProfile();
        profile2.setUser(testUser1); // Same user

        assertThrows(DataIntegrityViolationException.class, () -> {
            supportProfileRepository.saveAndFlush(profile2);
        });
    }
}
