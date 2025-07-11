package com.mysillydreams.userservice.repository.delivery;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.delivery.AssignmentStatus;
import com.mysillydreams.userservice.domain.delivery.AssignmentType;
import com.mysillydreams.userservice.domain.delivery.DeliveryProfile;
import com.mysillydreams.userservice.domain.delivery.OrderAssignment;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;


import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
public class OrderAssignmentRepositoryTest {

    @Autowired
    private OrderAssignmentRepository orderAssignmentRepository;
    @Autowired
    private DeliveryProfileRepository deliveryProfileRepository;
    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;
    private DeliveryProfile testDeliveryProfile;
    private UUID testOrderId1, testOrderId2;

    @BeforeEach
    void setUp() {
        orderAssignmentRepository.deleteAll();
        deliveryProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setReferenceId("oa-user-" + UUID.randomUUID());
        testUser.setEmail(testUser.getReferenceId() + "@example.com");
        testUser = userRepository.saveAndFlush(testUser);

        testDeliveryProfile = new DeliveryProfile();
        testDeliveryProfile.setUser(testUser);
        testDeliveryProfile.setActive(true);
        testDeliveryProfile = deliveryProfileRepository.saveAndFlush(testDeliveryProfile);

        testOrderId1 = UUID.randomUUID();
        testOrderId2 = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        orderAssignmentRepository.deleteAll();
        deliveryProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    private OrderAssignment createAndSaveAssignment(DeliveryProfile profile, UUID orderId, AssignmentType type, AssignmentStatus status) {
        OrderAssignment oa = new OrderAssignment();
        oa.setDeliveryProfile(profile);
        oa.setOrderId(orderId);
        oa.setType(type);
        oa.setStatus(status);
        // assignedAt and lastUpdatedAt will be set by Hibernate
        return orderAssignmentRepository.saveAndFlush(oa);
    }

    @Test
    void saveAndFindById_shouldPersistAndRetrieveAssignment() {
        OrderAssignment oa = createAndSaveAssignment(testDeliveryProfile, testOrderId1, AssignmentType.DELIVERY, AssignmentStatus.ASSIGNED);
        Optional<OrderAssignment> foundOpt = orderAssignmentRepository.findById(oa.getId());

        assertThat(foundOpt).isPresent();
        OrderAssignment found = foundOpt.get();
        assertThat(found.getDeliveryProfile().getId()).isEqualTo(testDeliveryProfile.getId());
        assertThat(found.getOrderId()).isEqualTo(testOrderId1);
        assertThat(found.getType()).isEqualTo(AssignmentType.DELIVERY);
        assertThat(found.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(found.getAssignedAt()).isNotNull();
        assertThat(found.getLastUpdatedAt()).isNotNull();
    }

    @Test
    void findByDeliveryProfile_returnsSortedAssignments() throws InterruptedException {
        OrderAssignment oa1 = createAndSaveAssignment(testDeliveryProfile, testOrderId1, AssignmentType.DELIVERY, AssignmentStatus.ASSIGNED);
        Thread.sleep(10); // Ensure different assignedAt
        OrderAssignment oa2 = createAndSaveAssignment(testDeliveryProfile, testOrderId2, AssignmentType.PICKUP, AssignmentStatus.EN_ROUTE);

        List<OrderAssignment> assignmentsAsc = orderAssignmentRepository.findByDeliveryProfile(testDeliveryProfile, Sort.by(Sort.Direction.ASC, "assignedAt"));
        assertThat(assignmentsAsc).hasSize(2).extracting(OrderAssignment::getId).containsExactly(oa1.getId(), oa2.getId());

        List<OrderAssignment> assignmentsDesc = orderAssignmentRepository.findByDeliveryProfile(testDeliveryProfile, Sort.by(Sort.Direction.DESC, "assignedAt"));
        assertThat(assignmentsDesc).hasSize(2).extracting(OrderAssignment::getId).containsExactly(oa2.getId(), oa1.getId());
    }

    @Test
    void findByDeliveryProfileId_returnsAssignments() {
        createAndSaveAssignment(testDeliveryProfile, testOrderId1, AssignmentType.DELIVERY, AssignmentStatus.ASSIGNED);
        List<OrderAssignment> assignments = orderAssignmentRepository.findByDeliveryProfileId(testDeliveryProfile.getId(), Sort.by("assignedAt"));
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getOrderId()).isEqualTo(testOrderId1);
    }


    @Test
    void findByDeliveryProfileAndStatusIn_returnsMatchingAssignments() {
        createAndSaveAssignment(testDeliveryProfile, testOrderId1, AssignmentType.DELIVERY, AssignmentStatus.ASSIGNED);
        createAndSaveAssignment(testDeliveryProfile, testOrderId2, AssignmentType.PICKUP, AssignmentStatus.EN_ROUTE);
        createAndSaveAssignment(testDeliveryProfile, UUID.randomUUID(), AssignmentType.DELIVERY, AssignmentStatus.COMPLETED);

        List<AssignmentStatus> statusesToFind = Arrays.asList(AssignmentStatus.ASSIGNED, AssignmentStatus.EN_ROUTE);
        List<OrderAssignment> foundAssignments = orderAssignmentRepository.findByDeliveryProfileAndStatusIn(
                testDeliveryProfile, statusesToFind, Sort.by("assignedAt"));

        assertThat(foundAssignments).hasSize(2);
        assertThat(foundAssignments).extracting(OrderAssignment::getOrderId).containsExactlyInAnyOrder(testOrderId1, testOrderId2);
    }

    @Test
    void findByOrderId_whenExists_returnsAssignment() {
        createAndSaveAssignment(testDeliveryProfile, testOrderId1, AssignmentType.DELIVERY, AssignmentStatus.ASSIGNED);
        Optional<OrderAssignment> foundOpt = orderAssignmentRepository.findByOrderId(testOrderId1);
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getDeliveryProfile().getId()).isEqualTo(testDeliveryProfile.getId());
    }

    @Test
    void findByDeliveryProfileAndStatusInAndDeliveryProfileActiveTrue_returnsCorrectly() {
        createAndSaveAssignment(testDeliveryProfile, testOrderId1, AssignmentType.DELIVERY, AssignmentStatus.ASSIGNED);
        createAndSaveAssignment(testDeliveryProfile, testOrderId2, AssignmentType.PICKUP, AssignmentStatus.COMPLETED); // Not active status

        List<AssignmentStatus> activeStatuses = List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.EN_ROUTE);
        List<OrderAssignment> result = orderAssignmentRepository.findByDeliveryProfileAndStatusInAndDeliveryProfileActiveTrue(
            testDeliveryProfile, activeStatuses, Sort.by("assignedAt")
        );
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo(testOrderId1);

        // Deactivate profile
        testDeliveryProfile.setActive(false);
        deliveryProfileRepository.saveAndFlush(testDeliveryProfile);

        result = orderAssignmentRepository.findByDeliveryProfileAndStatusInAndDeliveryProfileActiveTrue(
            testDeliveryProfile, activeStatuses, Sort.by("assignedAt")
        );
        assertThat(result).isEmpty();
    }
}
