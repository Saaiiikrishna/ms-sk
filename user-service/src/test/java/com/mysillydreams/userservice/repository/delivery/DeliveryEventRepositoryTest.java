package com.mysillydreams.userservice.repository.delivery;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.delivery.*;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
public class DeliveryEventRepositoryTest {

    @Autowired
    private DeliveryEventRepository deliveryEventRepository;
    @Autowired
    private OrderAssignmentRepository orderAssignmentRepository;
    @Autowired
    private DeliveryProfileRepository deliveryProfileRepository;
    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;
    private DeliveryProfile testDeliveryProfile;
    private OrderAssignment testOrderAssignment1;
    private OrderAssignment testOrderAssignment2;

    @BeforeEach
    void setUp() {
        deliveryEventRepository.deleteAll();
        orderAssignmentRepository.deleteAll();
        deliveryProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setReferenceId("evt-user-" + UUID.randomUUID());
        testUser.setEmail(testUser.getReferenceId() + "@example.com");
        testUser = userRepository.saveAndFlush(testUser);

        testDeliveryProfile = new DeliveryProfile();
        testDeliveryProfile.setUser(testUser);
        testDeliveryProfile = deliveryProfileRepository.saveAndFlush(testDeliveryProfile);

        testOrderAssignment1 = new OrderAssignment();
        testOrderAssignment1.setDeliveryProfile(testDeliveryProfile);
        testOrderAssignment1.setOrderId(UUID.randomUUID());
        testOrderAssignment1.setType(AssignmentType.DELIVERY);
        testOrderAssignment1 = orderAssignmentRepository.saveAndFlush(testOrderAssignment1);

        testOrderAssignment2 = new OrderAssignment(); // Another assignment for filtering tests
        testOrderAssignment2.setDeliveryProfile(testDeliveryProfile);
        testOrderAssignment2.setOrderId(UUID.randomUUID());
        testOrderAssignment2.setType(AssignmentType.PICKUP);
        testOrderAssignment2 = orderAssignmentRepository.saveAndFlush(testOrderAssignment2);
    }

    @AfterEach
    void tearDown() {
        deliveryEventRepository.deleteAll();
        orderAssignmentRepository.deleteAll();
        deliveryProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    private DeliveryEvent createAndSaveEvent(OrderAssignment assignment, String eventType, String payload) throws InterruptedException {
        // Add a small delay to ensure distinct timestamps for sorting if tests run fast
        Thread.sleep(5);
        DeliveryEvent event = new DeliveryEvent();
        event.setAssignment(assignment);
        event.setEventType(eventType);
        event.setPayload(payload);
        return deliveryEventRepository.saveAndFlush(event);
    }

    @Test
    void saveAndFindById_shouldPersistAndRetrieveEvent() throws InterruptedException {
        DeliveryEvent event = createAndSaveEvent(testOrderAssignment1, "ARRIVED", "{\"lat\":10, \"lon\":20}");
        Optional<DeliveryEvent> foundOpt = deliveryEventRepository.findById(event.getId());

        assertThat(foundOpt).isPresent();
        DeliveryEvent found = foundOpt.get();
        assertThat(found.getAssignment().getId()).isEqualTo(testOrderAssignment1.getId());
        assertThat(found.getEventType()).isEqualTo("ARRIVED");
        assertThat(found.getPayload()).isEqualTo("{\"lat\":10, \"lon\":20}");
        assertThat(found.getTimestamp()).isNotNull();
    }

    @Test
    void findByAssignment_returnsSortedEvents() throws InterruptedException {
        DeliveryEvent event1 = createAndSaveEvent(testOrderAssignment1, "EN_ROUTE", null, 0);
        DeliveryEvent event2 = createAndSaveEvent(testOrderAssignment1, "ARRIVED", "{}", 10); // 10ms later
        DeliveryEvent event3 = createAndSaveEvent(testOrderAssignment1, "PHOTO_TAKEN", "{}", 10); // 10ms later

        // Event for another assignment
        createAndSaveEvent(testOrderAssignment2, "OTHER_EVENT", null, 0);


        List<DeliveryEvent> eventsAsc = deliveryEventRepository.findByAssignment(testOrderAssignment1, Sort.by(Sort.Direction.ASC, "timestamp"));
        assertThat(eventsAsc).hasSize(3).extracting(DeliveryEvent::getId).containsExactly(event1.getId(), event2.getId(), event3.getId());

        List<DeliveryEvent> eventsDesc = deliveryEventRepository.findByAssignment(testOrderAssignment1, Sort.by(Sort.Direction.DESC, "timestamp"));
        assertThat(eventsDesc).hasSize(3).extracting(DeliveryEvent::getId).containsExactly(event3.getId(), event2.getId(), event1.getId());
    }

    @Test
    void findByAssignmentId_returnsSortedEvents() throws InterruptedException {
        DeliveryEvent event1 = createAndSaveEvent(testOrderAssignment1, "EVENT_A", null, 0);
        createAndSaveEvent(testOrderAssignment1, "EVENT_B", null, 10);

        List<DeliveryEvent> events = deliveryEventRepository.findByAssignmentId(testOrderAssignment1.getId(), Sort.by("timestamp"));
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getId()).isEqualTo(event1.getId());
    }


    @Test
    void findByAssignmentAndEventType_returnsMatchingEvents() throws InterruptedException {
        createAndSaveEvent(testOrderAssignment1, "PHOTO_TAKEN", "{\"s3Key\":\"key1\"}", 0);
        DeliveryEvent event2 = createAndSaveEvent(testOrderAssignment1, "OTP_VERIFIED", "{\"otp\":\"123\"}", 10);
        createAndSaveEvent(testOrderAssignment1, "PHOTO_TAKEN", "{\"s3Key\":\"key2\"}", 10); // Another photo

        List<DeliveryEvent> photoEvents = deliveryEventRepository.findByAssignmentAndEventType(testOrderAssignment1, "PHOTO_TAKEN", Sort.by("timestamp"));
        assertThat(photoEvents).hasSize(2);
        assertThat(photoEvents).extracting(DeliveryEvent::getPayload).containsExactly("{\"s3Key\":\"key1\"}", "{\"s3Key\":\"key2\"}");

        List<DeliveryEvent> otpEvents = deliveryEventRepository.findByAssignmentAndEventType(testOrderAssignment1, "OTP_VERIFIED", Sort.by("timestamp"));
        assertThat(otpEvents).hasSize(1);
        assertThat(otpEvents.get(0).getId()).isEqualTo(event2.getId());
    }

    @Test
    void countByAssignmentAndEventType_returnsCorrectCount() {
        createAndSaveEvent(testOrderAssignment1, "PHOTO_TAKEN", null);
        createAndSaveEvent(testOrderAssignment1, "PHOTO_TAKEN", null);
        createAndSaveEvent(testOrderAssignment1, "ARRIVED", null);
        createAndSaveEvent(testOrderAssignment2, "PHOTO_TAKEN", null); // Different assignment

        long photoCountForAssignment1 = deliveryEventRepository.countByAssignmentAndEventType(testOrderAssignment1, "PHOTO_TAKEN");
        assertThat(photoCountForAssignment1).isEqualTo(2);

        long arrivedCountForAssignment1 = deliveryEventRepository.countByAssignmentAndEventType(testOrderAssignment1, "ARRIVED");
        assertThat(arrivedCountForAssignment1).isEqualTo(1);

        long nonExistentCount = deliveryEventRepository.countByAssignmentAndEventType(testOrderAssignment1, "NON_EXISTENT");
        assertThat(nonExistentCount).isZero();
    }
}
