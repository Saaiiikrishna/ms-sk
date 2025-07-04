package com.mysillydreams.catalogservice.domain.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private CatalogItemEntity catalogItem;

    @Column(name = "rule_type", nullable = false, length = 100)
    private String ruleType; // e.g., "SELL_THROUGH_THRESHOLD", "TIME_OF_DAY_WINDOW", "INVENTORY_LEVEL_RATIO"

    @Type(JsonBinaryType.class)
    @Column(name = "parameters", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters; // Rule-specific parameters

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_by", length = 255)
    private String createdBy; // User or service identifier

    @Version
    private Long version; // For optimistic locking

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
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
