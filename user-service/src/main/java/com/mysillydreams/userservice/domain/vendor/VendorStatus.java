package com.mysillydreams.userservice.domain.vendor;

public enum VendorStatus {
    REGISTERED,        // Initial status upon registration
    KYC_IN_PROGRESS,   // KYC process has been initiated
    ACTIVE,            // KYC approved, vendor is active
    REJECTED,          // KYC rejected or other reasons
    REVIEW             // Needs manual review
    // Add other statuses as needed, e.g., SUSPENDED, DEACTIVATED
}
