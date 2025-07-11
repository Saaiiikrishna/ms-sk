package com.mysillydreams.vendor.service;

import com.mysillydreams.vendor.domain.VendorProfile;
import com.mysillydreams.vendor.dto.CreateVendorRequest;
import com.mysillydreams.vendor.dto.UpdateVendorRequest;

import java.util.UUID;

public interface VendorService {
  VendorProfile createVendor(CreateVendorRequest req);
  VendorProfile updateVendor(UUID id, UpdateVendorRequest req);
  VendorProfile getVendorById(UUID id);
  VendorProfile getVendorByUserId(UUID userId); // Added based on repository capability
}
