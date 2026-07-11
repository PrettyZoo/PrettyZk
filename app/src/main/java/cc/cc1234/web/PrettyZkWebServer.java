package cc.cc1234.web;

import cc.cc1234.web.api.ConfigApi;
import cc.cc1234.web.api.LogApi;
import cc.cc1234.web.api.NodeApi;
import cc.cc1234.web.api.ServerApi;
import cc.cc1234.web.api.TerminalApi;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PrettyZkWebServer {

    private static final Logger LOG = LoggerFactory.getLogger(PrettyZkWebServer.class);

    private final ServerApi serverApi;
    private final NodeApi nodeApi;
    private final ConfigApi configApi;
    private final TerminalApi terminalApi;
    private final LogApi logApi;

    private final NodeEventWsManager nodeEventWsManager;

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

        registerRoutes();
        app.start(host, port);
        LOG.info("PrettyZk server started on http://{}:{}", host, port);
    }

    public int getPort() { return app != null ? app.port() : -1; }

    public void stop() { if (app != null) app.stop(); }

    public void await() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void registerRoutes() {
        app.get("/", ctx -> ctx.redirect("/web/index.html"));
        app.get("/api/health", ctx -> ctx.result("{\"status\":\"ok\"}"));
        app.get("/api/version", configApi::getVersion);

        app.get("/api/servers", serverApi::listServers);
        app.post("/api/servers", serverApi::saveServer);
        app.get("/api/servers/{id}", serverApi::getServer);
        app.delete("/api/servers/{id}", serverApi::deleteServer);
        app.post("/api/servers/{id}/connect", ctx -> serverApi.connect(ctx, nodeEventWsManager));
        app.post("/api/servers/{id}/disconnect", serverApi::disconnect);

        app.get("/api/nodes/{serverId}", nodeApi::listNodes);
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
            ws.onError(ctx -> LOG.warn("Terminal WS error", ctx.error()));
        });

        app.sse("/api/logs/stream", client -> logApi.onConnect(client));
    }
}
