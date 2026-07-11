package cc.cc1234.web;

import cc.cc1234.specification.listener.NodeEvent;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages WebSocket sessions per server for real-time ZK node event push.
 */
public class NodeEventWsManager {

    private static final Logger LOG = LoggerFactory.getLogger(NodeEventWsManager.class);

    private final Map<String, Set<WsContext>> subscribers = new ConcurrentHashMap<>();

    public void subscribe(String serverId, WsContext ctx) {
        subscribers.computeIfAbsent(serverId, k -> new CopyOnWriteArraySet<>()).add(ctx);
        LOG.debug("Node events subscriber added for server {}", serverId);
    }

    public void unsubscribe(String serverId, WsContext ctx) {
        Set<WsContext> clients = subscribers.get(serverId);
        if (clients != null) {
            clients.remove(ctx);
            if (clients.isEmpty()) {
                subscribers.remove(serverId);
            }
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Broadcast a node event to all WebSocket subscribers of the given server.
     */
    public void broadcast(String serverId, String eventType, NodeEvent event) {
        Set<WsContext> clients = subscribers.get(serverId);
        if (clients == null || clients.isEmpty()) return;

        try {
            var msg = new java.util.LinkedHashMap<String, String>();
            msg.put("type", eventType);
            msg.put("path", event.getNode().getPath());
            msg.put("server", serverId);
            String json = JSON.writeValueAsString(msg);

            for (WsContext ctx : clients) {
                if (ctx.session.isOpen()) {
                    try {
                        ctx.send(json);
                    } catch (Exception e) {
                        LOG.warn("Failed to send WS event", e);
                        clients.remove(ctx);
                    }
                } else {
                    clients.remove(ctx);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to serialize WS event", e);
        }
    }
}
