package com.mysillydreams.vendor.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.vendor.domain.OutboxEvent;
import com.mysillydreams.vendor.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper; // Ensure ObjectMapper is available in the context

  /**
   * Publishes an event to the outbox. This method should be called
   * within an existing transaction that also saves the business aggregate.
   *
   * @param aggregateType The type of the aggregate (e.g., "VendorProfile").
   * @param aggregateId   The ID of the aggregate.
   * @param eventType     The type of the event (e.g., "vendor.profile.created").
   * @param payload       The event payload object, which will be serialized to JSON.
   */
  @Transactional(propagation = Propagation.MANDATORY) // Ensures this runs within an existing transaction
  public void publish(String aggregateType, String aggregateId,
                      String eventType, Object payload) {

    JsonNode jsonPayload = objectMapper.valueToTree(payload);

    OutboxEvent event = new OutboxEvent(
      UUID.randomUUID(),        // id
      aggregateType,            // aggregateType
      aggregateId,              // aggregateId
      eventType,                // eventType
      jsonPayload,              // payload
      false,                    // processed
      Instant.now()             // createdAt
    );
    outboxRepository.save(event);
  }
}
