package com.mysillydreams.userservice.domain.support;

import com.mysillydreams.userservice.domain.UserEntity;
import jakarta.persistence.*;
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
@Table(name = "support_profiles")
@Getter
@Setter
public class SupportProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user; // The user who is a support agent

    @Size(max = 255)
    @Column(length = 255)
    private String specialization; // E.g., "Billing Issues", "Technical Support Tier 1", "Order Disputes"
                                   // Could be an Enum or a separate lookup table if standardized.

    @Column(nullable = false)
    private boolean active = true; // Support agent can be active or inactive

    // One support profile can be assigned to many tickets
    @OneToMany(mappedBy = "assignedTo", fetch = FetchType.LAZY)
    private List<SupportTicket> assignedTickets = new ArrayList<>();


    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // Convenience methods for assignedTickets if needed, though typically managed by SupportTicket side
    public void addAssignedTicket(SupportTicket ticket) {
        assignedTickets.add(ticket);
        ticket.setAssignedTo(this);
    }

    public void removeAssignedTicket(SupportTicket ticket) {
        assignedTickets.remove(ticket);
        ticket.setAssignedTo(null);
    }
}
