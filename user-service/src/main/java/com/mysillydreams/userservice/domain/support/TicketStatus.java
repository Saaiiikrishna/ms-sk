package com.mysillydreams.userservice.domain.support;

public enum TicketStatus {
    OPEN,           // Ticket is newly created or re-opened
    IN_PROGRESS,    // Support agent is actively working on the ticket
    RESOLVED,       // A solution has been provided, awaiting customer confirmation or auto-closure
    CLOSED,         // Ticket is fully resolved and closed (distinct from RESOLVED if confirmation is needed)
    ESCALATED,      // Ticket has been escalated to a higher tier or different team
    PENDING_CUSTOMER_RESPONSE // Waiting for information from the customer
}
