package com.mysillydreams.userservice.repository.vendor;

import com.mysillydreams.userservice.domain.vendor.VendorDocument;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorDocumentRepository extends JpaRepository<VendorDocument, UUID> {

    /**
     * Finds all documents associated with a given vendor profile.
     *
     * @param vendorProfile The vendor profile whose documents are to be retrieved.
     * @return A list of {@link VendorDocument} belonging to the vendor profile.
     */
    List<VendorDocument> findByVendorProfile(VendorProfile vendorProfile);

    /**
     * Finds documents for a specific vendor profile and document type.
     * This can be used to check for existing documents of a certain type for a vendor.
     *
     * @param vendorProfile The vendor profile.
     * @param docType The type of the document (e.g., "PAN", "GSTIN").
     * @return A list of {@link VendorDocument} matching the criteria.
     */
    List<VendorDocument> findByVendorProfileAndDocType(VendorProfile vendorProfile, String docType);

    /**
     * Finds a vendor document by its unique S3 object key.
     *
     * @param s3Key The S3 object key.
     * @return An {@link Optional} containing the {@link VendorDocument} if found, or empty otherwise.
     */
    Optional<VendorDocument> findByS3Key(String s3Key);

    /**
     * Finds all documents for a specific vendor profile that have not yet been processed.
     *
     * @param vendorProfile The vendor profile.
     * @return A list of unprocessed {@link VendorDocument} for the vendor.
     */
    List<VendorDocument> findByVendorProfileAndProcessedFalse(VendorProfile vendorProfile);
}
