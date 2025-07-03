package com.mysillydreams.userservice.domain.support;

// import com.mysillydreams.userservice.converter.CryptoConverter; // If subject/description need encryption
import com.mysillydreams.userservice.domain.UserEntity; // For relationship if directly linking, or just store customerId as UUID
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "support_tickets", indexes = {
    @Index(name = "idx_supportticket_customerid", columnList = "customerId"),
    @Index(name = "idx_supportticket_status", columnList = "status"),
    @Index(name = "idx_supportticket_assignedto", columnList = "assigned_to_support_profile_id")
})
@Getter
@Setter
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Customer ID cannot be null.")
    @Column(nullable = false)
    private UUID customerId; // UUID of the UserEntity who is the customer for this ticket
    // Alternatively, if you want a direct FK and UserEntity is in same persistence unit:
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "customer_user_id", nullable = false)
    // private UserEntity customerUser;

    @ManyToOne(fetch = FetchType.LAZY) // A ticket can be assigned to one support agent (via their profile)
    @JoinColumn(name = "assigned_to_support_profile_id") // Nullable if unassigned or in a general queue
    private SupportProfile assignedTo;

    @NotBlank(message = "Subject cannot be blank.")
    @Size(max = 255)
    @Column(nullable = false)
    // TODO: SECURITY - Evaluate if subject can contain sensitive PII requiring field-level encryption.
    // If so: @Convert(converter = CryptoConverter.class) @Column(nullable = false, length = 1024)
    private String subject;

    @NotBlank(message = "Description cannot be blank.")
    @Column(nullable = false, columnDefinition = "TEXT")
    // TODO: SECURITY - Evaluate if description can contain sensitive PII requiring field-level encryption.
    // If so: @Convert(converter = CryptoConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Ticket status cannot be null.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TicketStatus status = TicketStatus.OPEN;

    // Could add priority, category, tags etc.
    // private Integer priority; // e.g., 1 (High) to 5 (Low)
    // @Column(length = 100)
    // private String category;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC") // Show messages in chronological order
    private List<SupportMessage> messages = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // Convenience methods for messages
    public void addMessage(SupportMessage message) {
        messages.add(message);
        message.setTicket(this);
    }

    public void removeMessage(SupportMessage message) {
        messages.remove(message);
        message.setTicket(null);
    }
}
