package com.mysillydreams.userservice.domain.support;

public enum SenderType {
    CUSTOMER,       // Message sent by the customer (UserEntity)
    SUPPORT_USER,   // Message sent by a support agent (UserEntity with SupportProfile)
    SYSTEM          // Message generated automatically by the system (e.g., auto-reply, status change notification)
}
