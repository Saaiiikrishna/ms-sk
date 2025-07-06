package com.mysillydreams.delivery.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysillydreams.delivery.dto.avro.GpsUpdateEvent; // Assuming GPS updates are sent as Avro via Kafka then to WS as JSON
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GpsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GpsWebSocketHandler.class);

    // assignmentId -> List of sessions subscribed to this assignment
    private final Map<UUID, List<WebSocketSession>> sessionsByAssignment = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper; // For converting GpsUpdateEvent to JSON string for WebSocket

    public GpsWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: Session ID - {}, URI - {}", session.getId(), session.getUri());
        // The client should send a message immediately after connection to subscribe to an assignmentId.
        // Example URI: /delivery-updates/gps?assignmentId=some-uuid
        // Or client sends a JSON message: {"type": "SUBSCRIBE", "assignmentId": "some-uuid"}
        // For simplicity, let's parse assignmentId from URI query param if present.
        UUID assignmentId = extractAssignmentId(session);
        if (assignmentId != null) {
            addSession(assignmentId, session);
        } else {
            log.warn("No assignmentId provided for session {}. Closing connection.", session.getId());
            // session.sendMessage(new TextMessage("{\"error\":\"assignmentId query parameter is required.\"}"));
            // session.close(CloseStatus.BAD_DATA.withReason("assignmentId query parameter is required."));
            // For now, just log. Subscription will happen via message.
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Received WebSocket message: {} from session: {}", payload, session.getId());

        try {
            // Expecting a JSON message like: {"type": "SUBSCRIBE", "assignmentId": "uuid-string"}
            // Or {"type": "UNSUBSCRIBE", "assignmentId": "uuid-string"}
            Map<String, String> command = objectMapper.readValue(payload, Map.class);
            String type = command.get("type");
            String assignmentIdStr = command.get("assignmentId");

            if (assignmentIdStr == null) {
                session.sendMessage(new TextMessage("{\"error\":\"assignmentId is required in command.\"}"));
                return;
            }
            UUID assignmentId = UUID.fromString(assignmentIdStr);

            if ("SUBSCRIBE".equalsIgnoreCase(type)) {
                addSession(assignmentId, session);
                session.sendMessage(new TextMessage("{\"status\":\"SUBSCRIBED\",\"assignmentId\":\"" + assignmentIdStr + "\"}"));
            } else if ("UNSUBSCRIBE".equalsIgnoreCase(type)) {
                removeSession(assignmentId, session);
                session.sendMessage(new TextMessage("{\"status\":\"UNSUBSCRIBED\",\"assignmentId\":\"" + assignmentIdStr + "\"}"));
            } else {
                session.sendMessage(new TextMessage("{\"error\":\"Unknown command type: " + type + "\"}"));
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message: {} from session: {}", payload, session.getId(), e);
            session.sendMessage(new TextMessage("{\"error\":\"Invalid message format.\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: Session ID - {}, Status - {}", session.getId(), status);
        // Remove session from all subscriptions
        sessionsByAssignment.forEach((assignmentId, sessionList) -> sessionList.remove(session));
        // Clean up empty lists (optional, can be done periodically)
        sessionsByAssignment.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(), exception);
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR.withReason(exception.getMessage()));
    }

    // Method to be called by a Kafka listener/service when a new GPS update is available
    public void sendGpsUpdateToSubscribers(GpsUpdateEvent gpsUpdate) {
        if (gpsUpdate == null || gpsUpdate.getAssignmentId() == null) {
            log.warn("Received null GPS update or update with null assignmentId.");
            return;
        }
        UUID assignmentId = UUID.fromString(gpsUpdate.getAssignmentId()); // Assuming assignmentId in Avro is String
        List<WebSocketSession> subscribedSessions = sessionsByAssignment.get(assignmentId);

        if (subscribedSessions != null && !subscribedSessions.isEmpty()) {
            try {
                // Convert Avro GpsUpdateEvent to JSON string to send over WebSocket
                String gpsUpdateJson = objectMapper.writeValueAsString(gpsUpdate);
                TextMessage message = new TextMessage(gpsUpdateJson);
                log.debug("Sending GPS update for assignment {}: {} to {} sessions", assignmentId, gpsUpdateJson, subscribedSessions.size());

                // Iterate over a copy in case of concurrent modifications, or use CopyOnWriteArrayList
                for (WebSocketSession session : new CopyOnWriteArrayList<>(subscribedSessions)) { // Iterate on a copy
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(message);
                        } catch (IOException e) {
                            log.error("IOException sending GPS update to session {}: {}", session.getId(), e.getMessage());
                            // Optionally remove session if send fails repeatedly
                            removeSession(assignmentId, session);
                        }
                    } else {
                        // Session closed, remove it
                        removeSession(assignmentId, session);
                    }
                }
            } catch (Exception e) {
                log.error("Error serializing or sending GPS update for assignment {}: {}", assignmentId, e.getMessage(), e);
            }
        } else {
            log.trace("No active WebSocket subscribers for GPS updates on assignmentId: {}", assignmentId);
        }
    }

    private void addSession(UUID assignmentId, WebSocketSession session) {
        sessionsByAssignment.computeIfAbsent(assignmentId, k -> new CopyOnWriteArrayList<>()).add(session);
        log.info("Session {} subscribed to GPS updates for assignmentId: {}", session.getId(), assignmentId);
    }

    private void removeSession(UUID assignmentId, WebSocketSession session) {
        List<WebSocketSession> sessionList = sessionsByAssignment.get(assignmentId);
        if (sessionList != null) {
            if(sessionList.remove(session)) {
                 log.info("Session {} unsubscribed from assignmentId: {}", session.getId(), assignmentId);
            }
            if (sessionList.isEmpty()) {
                sessionsByAssignment.remove(assignmentId);
            }
        }
    }

    private UUID extractAssignmentId(WebSocketSession session) {
        // Example: ws://host/delivery-updates/gps?assignmentId=uuid
        if (session.getUri() != null && session.getUri().getQuery() != null) {
            String query = session.getUri().getQuery();
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2 && "assignmentId".equals(pair[0])) {
                    try {
                        return UUID.fromString(pair[1]);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid assignmentId format in WebSocket URI query: {}", pair[1]);
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
