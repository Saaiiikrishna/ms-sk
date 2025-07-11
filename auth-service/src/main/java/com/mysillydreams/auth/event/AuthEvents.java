package com.mysillydreams.auth.event;

public final class AuthEvents {

    private AuthEvents() {
        // Private constructor to prevent instantiation
    }

    // Topic for authentication related events
    public static final String AUTH_EVENTS_TOPIC = "auth.events"; // As per PRD section 7.3

    // Specific event types within the auth.events topic
    public static final String PASSWORD_ROTATED = "auth.user.password_rotated"; // As per PRD section 3.1 & 4

    // You could define event payload structures or key names here as constants too if desired
    // public static final class PasswordRotatedPayload {
    //     public static final String USER_ID = "userId";
    //     public static final String ROTATED_AT = "rotatedAt";
    // }
}
