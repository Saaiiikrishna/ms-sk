package com.ecommerce.vendorfulfillmentservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "processed_inbound_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedInboundEvent {

    @Id
    @Column(name = "event_id") // Matches V1__Initial_schema.sql
    private String eventId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;

    @Column(name = "consumer_group", nullable = false)
    private String consumerGroup;
}
