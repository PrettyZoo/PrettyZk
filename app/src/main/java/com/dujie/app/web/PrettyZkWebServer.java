package com.dujie.app.web;

import com.dujie.app.web.api.ConfigApi;
import com.dujie.app.web.api.LogApi;
import com.dujie.app.web.api.NodeApi;
import com.dujie.app.web.api.ServerApi;
import com.dujie.app.web.api.TerminalApi;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.eclipse.jetty.websocket.api.exceptions.WebSocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class PrettyZkWebServer {

    private static final Logger LOG = LoggerFactory.getLogger(PrettyZkWebServer.class);

    private final ServerApi serverApi;
    private final NodeApi nodeApi;
    private final ConfigApi configApi;
    private final TerminalApi terminalApi;
    private final LogApi logApi;

    private final NodeEventWsManager nodeEventWsManager;

    // The Tauri webview serves the UI from its own origin, so the API is
    // cross-origin there. Javalin's built-in CORS plugin cannot parse origins
    // without explicit ports (tauri://localhost), hence this small allowlist.
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "tauri://localhost",
            "http://tauri.localhost",
            "https://tauri.localhost",
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    );

    private volatile Javalin app;

    public PrettyZkWebServer(ServerApi serverApi, NodeApi nodeApi,
                              ConfigApi configApi, TerminalApi terminalApi,
                              LogApi logApi) {
        this.serverApi = serverApi;
        this.nodeApi = nodeApi;
        this.configApi = configApi;
        this.terminalApi = terminalApi;
        this.logApi = logApi;
        this.nodeEventWsManager = new NodeEventWsManager();
    }

    public NodeEventWsManager getNodeEventWsManager() {
        return nodeEventWsManager;
    }

    public void start(String host, int port) {
        app = Javalin.create(config -> {
            // The desktop terminal can sit idle for a long time. Keep WebSocket
            // connections alive instead of letting Jetty close them after 30s.
            config.jetty.modifyWebSocketServletFactory(factory ->
                    factory.setIdleTimeout(Duration.ofHours(24)));
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/web";
                staticFiles.directory = "/web";
                staticFiles.location = Location.CLASSPATH;
            });
            config.showJavalinBanner = false;
            config.http.defaultContentType = "application/json";
            config.requestLogger.http((ctx, timeMs) -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[{}] {} {} - {}ms", ctx.method(), ctx.path(), ctx.status(), timeMs);
                }
            });
        });

        // Global exception handler
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Unhandled exception in {} {}", ctx.method(), ctx.path(), e);
            ctx.status(500);
            ctx.json(Map.of("error", "Internal server error"));
        });

        registerCors();
        registerRoutes();
        app.start(host, port);
        LOG.info("PrettyZk server started on http://{}:{}", host, port);
    }

    public int getPort() {
        return app != null ? app.port() : -1;
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    public void await() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void registerCors() {
        app.before(ctx -> {
            String origin = ctx.header("Origin");
            if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                ctx.header("Access-Control-Allow-Origin", origin);
                ctx.header("Vary", "Origin");
                ctx.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
                ctx.header("Access-Control-Allow-Headers", "Content-Type");
                ctx.header("Access-Control-Max-Age", "86400");
            }
        });
        // Answer preflight requests; the filter above sets the CORS headers.
        app.options("/*", ctx -> ctx.status(204));
    }

    private void registerRoutes() {
        app.get("/", ctx -> ctx.redirect("/web/index.html"));
        app.get("/api/health", ctx -> ctx.result("{\"status\":\"ok\"}"));
        app.get("/api/version", configApi::getVersion);
        app.get("/api/version/latest", configApi::checkLatestVersion);

        app.get("/api/servers", serverApi::listServers);
        app.post("/api/servers", serverApi::saveServer);
        app.get("/api/servers/{id}", serverApi::getServer);
        app.delete("/api/servers/{id}", serverApi::deleteServer);
        app.post("/api/servers/{id}/connect", ctx -> serverApi.connect(ctx, nodeEventWsManager));
        app.post("/api/servers/{id}/disconnect", serverApi::disconnect);

        app.get("/api/nodes/{serverId}/children", nodeApi::listChildren);
        app.get("/api/nodes/{serverId}/detail", nodeApi::getNodeDetail);
        app.post("/api/nodes/{serverId}", nodeApi::createNode);
        app.delete("/api/nodes/{serverId}", nodeApi::deleteNode);
        app.put("/api/nodes/{serverId}", nodeApi::updateNode);
        app.get("/api/nodes/{serverId}/search", nodeApi::searchNodes);
        app.post("/api/nodes/{serverId}/4lc", nodeApi::executeFourLetterCmd);

        app.ws("/ws/nodes/{serverId}", ws -> {
            ws.onConnect(ctx -> nodeEventWsManager.subscribe(ctx.pathParam("serverId"), ctx));
            ws.onClose(ctx -> {
                String sid = ctx.pathParam("serverId");
                nodeEventWsManager.unsubscribe(sid, ctx);
                LOG.debug("Node events WS closed for {}", sid);
            });
        });

        app.get("/api/config", configApi::getConfig);
        app.put("/api/config/theme", configApi::updateTheme);
        app.put("/api/config/font-size", configApi::updateFontSize);
        app.put("/api/config/locale", configApi::updateLocale);
        app.post("/api/config/export", configApi::exportConfig);
        app.post("/api/config/import", configApi::importConfig);

        app.ws("/ws/terminal/{serverId}", ws -> {
            ws.onConnect(terminalApi::onConnect);
            ws.onMessage(terminalApi::onMessage);
            ws.onClose(terminalApi::onClose);
            ws.onError(ctx -> {
                Throwable error = ctx.error();
                if (error instanceof WebSocketTimeoutException) {
                    LOG.debug("Terminal WS idle timeout");
                } else {
                    LOG.warn("Terminal WS error: {}", error.toString());
                }
            });
        });

        app.sse("/api/logs/stream", client -> logApi.onConnect(client));
    }
}
