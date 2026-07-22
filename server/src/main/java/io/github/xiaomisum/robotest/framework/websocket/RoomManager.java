package io.github.xiaomisum.robotest.framework.websocket;

import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final Map<String, CopyOnWriteArraySet<Session>> rooms = new ConcurrentHashMap<>();

    public void joinRoom(String docId, Session session) {
        rooms.computeIfAbsent(docId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void leaveRoom(String docId, Session session) {
        CopyOnWriteArraySet<Session> room = rooms.get(docId);
        if (room != null) {
            room.remove(session);
            if (room.isEmpty()) {
                rooms.remove(docId);
            }
        }
    }

    public void broadcast(String docId, String message, Session sender) {
        CopyOnWriteArraySet<Session> room = rooms.get(docId);
        if (room == null) {
            return;
        }

        for (Session session : room) {
            if (session.isOpen() && !session.equals(sender)) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public int getOnlineCount(String docId) {
        CopyOnWriteArraySet<Session> room = rooms.get(docId);
        return room != null ? room.size() : 0;
    }

    public Set<String> getActiveDocIds() {
        return rooms.keySet();
    }
}
