package com.mysillydreams.userservice.repository.vendor;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.vendor.VendorDocument;
import com.mysillydreams.userservice.domain.vendor.VendorProfile;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
public class VendorDocumentRepositoryTest {

    @Autowired
    private VendorDocumentRepository vendorDocumentRepository;

    @Autowired
    private VendorProfileRepository vendorProfileRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;
    private VendorProfile testVendorProfile;

    @BeforeEach
    void setUp() {
        vendorDocumentRepository.deleteAll();
        vendorProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setReferenceId(UUID.randomUUID().toString());
        testUser.setEmail("doc.user." + UUID.randomUUID() + "@example.com");
        testUser.setName("Doc Test User");
        testUser = userRepository.saveAndFlush(testUser);

        testVendorProfile = new VendorProfile();
        testVendorProfile.setUser(testUser);
        testVendorProfile.setLegalName("Doc Vendor Profile");
        testVendorProfile = vendorProfileRepository.saveAndFlush(testVendorProfile);
    }

    @AfterEach
    void tearDown() {
        vendorDocumentRepository.deleteAll();
        vendorProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    private VendorDocument createAndSaveDocument(String docType, String s3Key, boolean processed) {
        VendorDocument doc = new VendorDocument();
        doc.setVendorProfile(testVendorProfile);
        doc.setDocType(docType);
        doc.setS3Key(s3Key);
        doc.setChecksum("checksum-" + s3Key);
        doc.setProcessed(processed);
        return vendorDocumentRepository.saveAndFlush(doc);
    }

    @Test
    void saveAndFindById_shouldPersistAndRetrieveDocument() {
        VendorDocument doc = createAndSaveDocument("PAN_CARD", "s3://bucket/pan123", false);

        Optional<VendorDocument> foundDocOpt = vendorDocumentRepository.findById(doc.getId());

        assertThat(foundDocOpt).isPresent();
        VendorDocument foundDoc = foundDocOpt.get();
        assertThat(foundDoc.getVendorProfile().getId()).isEqualTo(testVendorProfile.getId());
        assertThat(foundDoc.getDocType()).isEqualTo("PAN_CARD");
        assertThat(foundDoc.getS3Key()).isEqualTo("s3://bucket/pan123");
        assertThat(foundDoc.getChecksum()).isEqualTo("checksum-s3://bucket/pan123");
        assertThat(foundDoc.isProcessed()).isFalse();
        assertThat(foundDoc.getUploadedAt()).isNotNull();
    }

    @Test
    void findByVendorProfile_returnsAllDocumentsForProfile() {
        createAndSaveDocument("PAN", "s3key1", false);
        createAndSaveDocument("GSTIN", "s3key2", true);

        // Create doc for another vendor profile to ensure filtering
        UserEntity otherUser = userRepository.save(new UserEntity(){{
            setReferenceId(UUID.randomUUID().toString());
            setEmail("other.doc.user."+UUID.randomUUID()+"@example.com");
        }});
        VendorProfile otherProfile = vendorProfileRepository.save(new VendorProfile(){{ setUser(otherUser); setLegalName("Other"); }});
        createAndSaveDocument("OTHER_DOC", "s3keyOther", false);


        List<VendorDocument> docs = vendorDocumentRepository.findByVendorProfile(testVendorProfile);

        assertThat(docs).hasSize(2);
        assertThat(docs).extracting(VendorDocument::getS3Key).containsExactlyInAnyOrder("s3key1", "s3key2");
    }

    @Test
    void findByVendorProfileAndDocType_returnsMatchingDocuments() {
        createAndSaveDocument("PAN", "s3keyPan1", false);
        createAndSaveDocument("PAN", "s3keyPan2", true);
        createAndSaveDocument("GSTIN", "s3keyGst1", false);

        List<VendorDocument> panDocs = vendorDocumentRepository.findByVendorProfileAndDocType(testVendorProfile, "PAN");
        List<VendorDocument> gstinDocs = vendorDocumentRepository.findByVendorProfileAndDocType(testVendorProfile, "GSTIN");
        List<VendorDocument> aadhaarDocs = vendorDocumentRepository.findByVendorProfileAndDocType(testVendorProfile, "AADHAAR");


        assertThat(panDocs).hasSize(2);
        assertThat(panDocs).extracting(VendorDocument::getS3Key).containsExactlyInAnyOrder("s3keyPan1", "s3keyPan2");
        assertThat(gstinDocs).hasSize(1);
        assertThat(gstinDocs.get(0).getS3Key()).isEqualTo("s3keyGst1");
        assertThat(aadhaarDocs).isEmpty();
    }

    @Test
    void findByS3Key_whenKeyExists_returnsDocument() {
        String targetS3Key = "uniqueS3KeyTarget";
        createAndSaveDocument("INVOICE", targetS3Key, false);
        createAndSaveDocument("REPORT", "anotherS3Key", true);

        Optional<VendorDocument> foundDocOpt = vendorDocumentRepository.findByS3Key(targetS3Key);

        assertThat(foundDocOpt).isPresent();
        assertThat(foundDocOpt.get().getDocType()).isEqualTo("INVOICE");
    }

    @Test
    void findByS3Key_whenKeyDoesNotExist_returnsEmpty() {
        Optional<VendorDocument> foundDocOpt = vendorDocumentRepository.findByS3Key("non-existent-s3-key");
        assertThat(foundDocOpt).isNotPresent();
    }

    @Test
    void findByVendorProfileAndProcessedFalse_returnsOnlyUnprocessedDocuments() {
        createAndSaveDocument("DOC_A", "s3_A", false); // Unprocessed
        createAndSaveDocument("DOC_B", "s3_B", true);  // Processed
        createAndSaveDocument("DOC_C", "s3_C", false); // Unprocessed

        List<VendorDocument> unprocessedDocs = vendorDocumentRepository.findByVendorProfileAndProcessedFalse(testVendorProfile);

        assertThat(unprocessedDocs).hasSize(2);
        assertThat(unprocessedDocs).extracting(VendorDocument::getS3Key).containsExactlyInAnyOrder("s3_A", "s3_C");
        assertThat(unprocessedDocs).allMatch(doc -> !doc.isProcessed());
    }
}
