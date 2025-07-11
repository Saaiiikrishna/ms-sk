package com.mysillydreams.vendor.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="vendor_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendorProfile {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO) // Standard JPA way, works well with UUIDs and Postgres
  private UUID id;

  @Column(name="user_id", nullable=false, unique=true) // Ensure user_id is unique
  private UUID userId;

  @Column(nullable=false) // Name should not be null
  private String name;

  @Column(name="legal_type")
  private String legalType;

  @Type(JsonType.class) // Using Hypersistence Utils for JSONB
  @Column(name="contact_info", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON) // Recommended for Hibernate 6+
  private JsonNode contactInfo;

  @Type(JsonType.class) // Using Hypersistence Utils for JSONB
  @Column(name="bank_details", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON) // Recommended for Hibernate 6+
  private JsonNode bankDetails;

  @Column(name="kyc_status")
  private String kycStatus;

  @CreationTimestamp
  @Column(name="created_at", nullable=false, updatable=false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name="updated_at", nullable=false)
  private Instant updatedAt;
}
