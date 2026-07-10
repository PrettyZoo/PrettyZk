package cc.cc1234.web.api;

import cc.cc1234.core.configuration.entity.ServerConfiguration;
import cc.cc1234.core.configuration.service.ConfigurationDomainService;
import cc.cc1234.core.zookeeper.service.ZookeeperDomainService;
import cc.cc1234.specification.util.StringWriter;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TerminalApi {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalApi.class);

    private final ZookeeperDomainService zookeeperDomainService;
    private final ConfigurationDomainService configurationDomainService;

    private final Map<WsContext, TerminalSession> sessions = new ConcurrentHashMap<>();

    public TerminalApi(ZookeeperDomainService zookeeperDomainService,
                       ConfigurationDomainService configurationDomainService) {
        this.zookeeperDomainService = zookeeperDomainService;
        this.configurationDomainService = configurationDomainService;
    }

    public void onConnect(WsConnectContext ctx) {
        String serverId = ctx.pathParam("serverId");
        LOG.debug("Terminal WS connected for server {}", serverId);

        var serverOpt = configurationDomainService.getById(serverId);
        if (serverOpt.isEmpty()) {
            ctx.send(Map.of("error", "Server not found: " + serverId));
            ctx.session.close();
            return;
        }

        ServerConfiguration server = serverOpt.get();
        String urlToConnect;
        if (server.getSshTunnelEnabled()) {
            urlToConnect = "localhost:" + server.getPort();
        } else {
            urlToConnect = server.getHost() + ":" + server.getPort();
        }

        var outputWriter = new StringWriter() {
            @Override
            public void write(String str) {
                if (ctx.session.isOpen()) {
                    ctx.send(Map.of("type", "output", "data", str));
                }
            }
        };

        var session = new TerminalSession(serverId, outputWriter);
        sessions.put(ctx, session);

        try {
            zookeeperDomainService.initTerminal(serverId, urlToConnect, outputWriter);
            ctx.send(Map.of("type", "info", "data", "Terminal connected to " + urlToConnect));
        } catch (Exception e) {
            LOG.error("Failed to init terminal for server {}", serverId, e);
            ctx.send(Map.of("type", "error", "data", "Connection failed: " + e.getMessage()));
        }
    }

    public void onMessage(WsMessageContext ctx) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = ctx.messageAsClass(Map.class);
            String command = (String) message.get("command");

            if (command == null || command.isBlank()) {
                return;
            }

            TerminalSession session = sessions.get(ctx);
            if (session == null) {
                ctx.send(Map.of("type", "error", "data", "No terminal session active"));
                return;
            }

            if ("clear".equalsIgnoreCase(command.trim())) {
                ctx.send(Map.of("type", "clear"));
                return;
            }

            zookeeperDomainService.execute(session.serverId, command);
        } catch (Exception e) {
            LOG.error("Terminal command error", e);
            ctx.send(Map.of("type", "error", "data", "Command failed: " + e.getMessage()));
        }
    }

    public void onClose(WsContext ctx) {
        TerminalSession session = sessions.remove(ctx);
        if (session != null) {
            LOG.debug("Terminal WS closed for server {}", session.serverId);
        }
    }

    public void onError(WsContext ctx, Throwable error) {
        LOG.error("Terminal WS error", error);
        sessions.remove(ctx);
    }

    private static class TerminalSession {
        final String serverId;
        final StringWriter outputWriter;

        TerminalSession(String serverId, StringWriter outputWriter) {
            this.serverId = serverId;
            this.outputWriter = outputWriter;
        }
    }
}
