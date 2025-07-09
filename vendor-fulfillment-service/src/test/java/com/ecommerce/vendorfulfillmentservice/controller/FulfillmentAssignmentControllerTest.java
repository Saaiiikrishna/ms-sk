package com.ecommerce.vendorfulfillmentservice.controller;

import com.ecommerce.vendorfulfillmentservice.controller.dto.ReassignAssignmentRequest;
import com.ecommerce.vendorfulfillmentservice.controller.dto.ShipAssignmentRequest;
import com.ecommerce.vendorfulfillmentservice.entity.AssignmentStatus;
import com.ecommerce.vendorfulfillmentservice.entity.VendorOrderAssignment;
import com.ecommerce.vendorfulfillmentservice.service.VendorAssignmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FulfillmentAssignmentController.class)
class FulfillmentAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VendorAssignmentService vendorAssignmentService;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID testAssignmentId = UUID.randomUUID();
    private final UUID testOrderId = UUID.randomUUID();
    private final UUID testVendorId = UUID.randomUUID();
    private final UUID testNewVendorId = UUID.randomUUID();

    private VendorOrderAssignment createDummyAssignment() {
        return VendorOrderAssignment.builder()
                .id(testAssignmentId)
                .orderId(testOrderId)
                .vendorId(testVendorId)
                .status(AssignmentStatus.ASSIGNED)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // --- Test /ack endpoint ---
    @Test
    @WithMockUser(roles = {"VENDOR"})
    void whenAckAsVendor_thenReturnsOk() throws Exception {
        when(vendorAssignmentService.acknowledgeOrder(testAssignmentId)).thenReturn(createDummyAssignment());

        mockMvc.perform(post("/fulfillment/assignments/{id}/ack", testAssignmentId)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"}) // ADMIN should not access VENDOR specific ack
    void whenAckAsAdmin_thenReturnsForbidden() throws Exception {
        mockMvc.perform(post("/fulfillment/assignments/{id}/ack", testAssignmentId)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAckAsUnauthenticated_thenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/fulfillment/assignments/{id}/ack", testAssignmentId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }


    // --- Test /reassign endpoint ---
    @Test
    @WithMockUser(roles = {"ADMIN"})
    void whenReassignAsAdmin_thenReturnsOk() throws Exception {
        ReassignAssignmentRequest request = new ReassignAssignmentRequest(testNewVendorId);
        // Assuming reassignOrder returns the assignment, and then controller fetches DTO
        when(vendorAssignmentService.reassignOrder(eq(testAssignmentId), eq(testNewVendorId), any()))
                .thenReturn(createDummyAssignment());
        // Mocking the get by ID call that happens after reassign in controller
        when(vendorAssignmentService.findAssignmentByIdWithHistory(testAssignmentId))
                .thenReturn(null); // Actual DTO mapping not tested here, just service call

        mockMvc.perform(put("/fulfillment/assignments/{id}/reassign", testAssignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"VENDOR"})
    void whenReassignAsVendor_thenReturnsForbidden() throws Exception {
        ReassignAssignmentRequest request = new ReassignAssignmentRequest(testNewVendorId);
        mockMvc.perform(put("/fulfillment/assignments/{id}/reassign", testAssignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenReassignAsUnauthenticated_thenReturnsUnauthorized() throws Exception {
        ReassignAssignmentRequest request = new ReassignAssignmentRequest(testNewVendorId);
        mockMvc.perform(put("/fulfillment/assignments/{id}/reassign", testAssignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // TODO: Add similar tests for /pack, /ship, /complete and GET endpoints.
    // TODO: For VENDOR specific endpoints, add tests to ensure a vendor cannot access another vendor's assignments (once principal/vendorId matching is implemented).
}
