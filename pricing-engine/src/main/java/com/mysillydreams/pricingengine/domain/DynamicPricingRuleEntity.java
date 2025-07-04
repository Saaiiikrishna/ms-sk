package com.mysillydreams.pricingengine.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType; // Updated import
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp; // Corrected from vladmihalcea
import org.hibernate.annotations.Type; // Standard Hibernate @Type

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "dynamic_pricing_rules", indexes = {
    @Index(name = "idx_dpr_item_id", columnList = "item_id"),
    @Index(name = "idx_dpr_rule_type", columnList = "rule_type"),
    @Index(name = "idx_dpr_enabled", columnList = "enabled")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamicPricingRuleEntity {

    @Id
    // @GeneratedValue(strategy = GenerationType.AUTO) // ID will be set from the consumed event
    private UUID id;

    // Changed from CatalogItemEntity to UUID to store only the ID
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "rule_type", nullable = false, length = 100)
    private String ruleType;

    @Type(JsonBinaryType.class)
    @Column(name = "parameters", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicPricingRuleEntity that)) return false;
        // For entities managed by JPA, business key equality is preferred if ID can be null before persistence.
        // However, since ID is set from Kafka event (presumably never null when saving), ID-based is fine.
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // If using ID for equals, use ID for hashCode.
        // getClass().hashCode() is problematic if subclasses are involved or if ID can be null.
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
