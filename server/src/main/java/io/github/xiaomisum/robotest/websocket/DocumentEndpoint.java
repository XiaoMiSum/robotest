package io.github.xiaomisum.robotest.websocket;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ServerEndpoint("/ws/documents/{docId}")
public class DocumentEndpoint {

    private static final Logger log = LoggerFactory.getLogger(DocumentEndpoint.class);

    private static RoomManager roomManager;
    private static DocumentPersistenceHandler persistenceHandler;

    private String docId;
    private String userId;
    private Session session;

    @Autowired
    public void setRoomManager(RoomManager roomManager) {
        DocumentEndpoint.roomManager = roomManager;
    }

    @Autowired
    public void setPersistenceHandler(DocumentPersistenceHandler persistenceHandler) {
        DocumentEndpoint.persistenceHandler = persistenceHandler;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("docId") String docId) {
        this.docId = docId;
        this.session = session;
        this.userId = session.getUserProperties().getOrDefault("userId", "anonymous").toString();

        roomManager.joinRoom(docId, session);
        log.info("User {} joined document room {}", userId, docId);
    }

    @OnMessage
    public void onMessage(String message, @PathParam("docId") String docId) {
        roomManager.broadcast(docId, message, session);

        try {
            persistenceHandler.persist(docId, message, session);
        } catch (Exception e) {
            log.error("Failed to persist WebSocket message for doc {}: {}", docId, e.getMessage(), e);
        }
    }

    @OnClose
    public void onClose(@PathParam("docId") String docId) {
        roomManager.leaveRoom(docId, session);
        log.info("User {} left document room {}", userId, docId);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error for user {} in doc {}: {}", userId, docId, error.getMessage());
    }
}
