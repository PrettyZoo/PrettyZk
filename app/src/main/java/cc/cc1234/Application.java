package cc.cc1234;

import cc.cc1234.core.configuration.service.ConfigurationDomainService;
import cc.cc1234.core.zookeeper.service.ZookeeperDomainService;
import cc.cc1234.web.PrettyZooWebServer;
import cc.cc1234.web.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;

public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        // Parse command line arguments
        boolean webMode = false;
        int port = findFreePort();
        String host = "127.0.0.1";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--web":
                case "-w":
                    webMode = true;
                    host = "0.0.0.0";
                    break;
                case "--port":
                case "-p":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            LOG.warn("Invalid port number: {}, using random port", args[i]);
                        }
                    }
                    break;
                case "--host":
                case "-h":
                    if (i + 1 < args.length) {
                        host = args[++i];
                    }
                    break;
            }
        }

        // Initialize backend
        LOG.info("Starting PrettyZoo...");
        var configDomainService = new ConfigurationDomainService();
        var zkDomainService = new ZookeeperDomainService();

        // Create API handlers
        var serverApi = new ServerApi(zkDomainService, configDomainService);
        var nodeApi = new NodeApi(zkDomainService);
        var configApi = new ConfigApi(configDomainService);
        var terminalApi = new TerminalApi(zkDomainService, configDomainService);
        var logApi = new LogApi();

        // Start HTTP server
        PrettyZooWebServer server = new PrettyZooWebServer(
                serverApi, nodeApi, configApi, terminalApi, logApi
        );
        server.start(host, port);
        port = server.getPort();

        if (!webMode) {
            // Desktop mode: open system browser
            String url = "http://127.0.0.1:" + port;
            LOG.info("Opening browser to {}", url);
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                } else {
                    LOG.warn("Desktop browse not supported. Please open {} manually.", url);
                }
            } catch (IOException e) {
                LOG.error("Failed to open browser: {}", e.getMessage());
                LOG.info("Please open {} in your browser.", url);
            }
        } else {
            // Web deployment mode
            LOG.info("=============================================");
            LOG.info("  PrettyZoo Web Server started");
            LOG.info("  Listen on http://{}:{}", host, port);
            LOG.info("=============================================");
        }

        // Keep the server running
        server.await();
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 8080; // fallback
        }
    }
}
