package com.mysillydreams.userservice.domain.support;

// import com.mysillydreams.userservice.converter.CryptoConverter; // If message needs encryption
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp; // For timestamp

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_messages")
@Getter
@Setter
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Support ticket cannot be null for a message.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @NotNull(message = "Sender type cannot be null.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SenderType senderType; // CUSTOMER, SUPPORT_USER, SYSTEM

    @NotNull(message = "Sender ID cannot be null.")
    @Column(nullable = false)
    private UUID senderId; // UUID of the UserEntity (customer or support agent) or a system ID

    @NotBlank(message = "Message content cannot be blank.")
    @Column(nullable = false, columnDefinition = "TEXT")
    // TODO: SECURITY - Evaluate if message content can contain sensitive PII requiring field-level encryption.
    // If so: @Convert(converter = CryptoConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT") // Store as JSON string: e.g., "[{\"s3Key\":\"...\", \"filename\":\"...\"}, ...]"
    private String attachments; // JSON array of attachment metadata (e.g., S3 keys, filenames)
                                // Individual attachment files would be stored in S3 or similar.

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp; // When the message was sent/created
}
