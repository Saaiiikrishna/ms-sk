package com.mysillydreams.vendor.service;

import com.mysillydreams.vendor.domain.VendorProfile;
import com.mysillydreams.vendor.dto.CreateVendorRequest;
import com.mysillydreams.vendor.dto.UpdateVendorRequest;
import com.mysillydreams.vendor.dto.avro.VendorProfileEvent; // Corrected import for Avro DTO
import com.mysillydreams.vendor.event.OutboxEventService;
import com.mysillydreams.vendor.exception.ResourceNotFoundException;
import com.mysillydreams.vendor.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {
  private final VendorProfileRepository vendorProfileRepository;
  private final OutboxEventService outboxEventService;

  @Override
  @Transactional
  public VendorProfile createVendor(CreateVendorRequest req) {
    // Optional: Check if a vendor profile already exists for this userId
    vendorProfileRepository.findByUserId(req.getUserId()).ifPresent(vp -> {
        throw new IllegalStateException("Vendor profile already exists for user ID: " + req.getUserId());
    });

    VendorProfile vendorProfile = new VendorProfile();
    vendorProfile.setUserId(req.getUserId());
    vendorProfile.setName(req.getName());
    vendorProfile.setLegalType(req.getLegalType());
    vendorProfile.setContactInfo(req.getContactInfo());
    vendorProfile.setBankDetails(req.getBankDetails());
    vendorProfile.setKycStatus("REGISTERED"); // Initial KYC status

    VendorProfile savedVendorProfile = vendorProfileRepository.save(vendorProfile);

    // Publish event to outbox
    // The Avro-generated VendorProfileEvent might have a different constructor or builder
    // Assuming a constructor that matches the fields for now.
    // Ensure the Avro generated class has matching constructor or use its builder.
    VendorProfileEvent eventPayload = new VendorProfileEvent(
      savedVendorProfile.getId().toString(),
      savedVendorProfile.getUserId().toString(),
      savedVendorProfile.getName(),
      savedVendorProfile.getLegalType(),
      savedVendorProfile.getKycStatus(),
      Instant.now().toEpochMilli()
    );

    outboxEventService.publish(
        "VendorProfile",
        savedVendorProfile.getId().toString(),
        "vendor.profile.created",
        eventPayload
    );

    return savedVendorProfile;
  }

  @Override
  @Transactional
  public VendorProfile updateVendor(UUID id, UpdateVendorRequest req) {
    VendorProfile vendorProfile = vendorProfileRepository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("VendorProfile", id));

    // Apply updates from request
    vendorProfile.setName(req.getName());
    // Note: legalType is not in UpdateVendorRequest as per spec, so it's not updated here.
    // If userId needs to be updated, consider implications (e.g., is it allowed? unique constraints?)
    vendorProfile.setContactInfo(req.getContactInfo());
    vendorProfile.setBankDetails(req.getBankDetails());
    vendorProfile.setKycStatus(req.getKycStatus());
    // updatedAt will be handled by @UpdateTimestamp

    VendorProfile updatedVendorProfile = vendorProfileRepository.save(vendorProfile);

    // Publish event to outbox
    VendorProfileEvent eventPayload = new VendorProfileEvent(
      updatedVendorProfile.getId().toString(),
      updatedVendorProfile.getUserId().toString(),
      updatedVendorProfile.getName(),
      updatedVendorProfile.getLegalType(), // current legalType
      updatedVendorProfile.getKycStatus(),
      Instant.now().toEpochMilli()
    );

    outboxEventService.publish(
        "VendorProfile",
        updatedVendorProfile.getId().toString(),
        "vendor.profile.updated",
        eventPayload
    );

    return updatedVendorProfile;
  }

  @Override
  @Transactional(readOnly = true)
  public VendorProfile getVendorById(UUID id) {
    return vendorProfileRepository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("VendorProfile", id));
  }

  @Override
  @Transactional(readOnly = true)
  public VendorProfile getVendorByUserId(UUID userId) {
    return vendorProfileRepository.findByUserId(userId)
      .orElseThrow(() -> new ResourceNotFoundException("VendorProfile", "userId", userId.toString()));
  }
}
