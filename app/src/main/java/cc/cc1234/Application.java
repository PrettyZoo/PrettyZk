package cc.cc1234;

import cc.cc1234.core.configuration.service.ConfigurationDomainService;
import cc.cc1234.core.zookeeper.service.ZookeeperDomainService;
import cc.cc1234.web.PrettyZkWebServer;
import cc.cc1234.web.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ServerSocket;

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
        LOG.info("Starting PrettyZk...");
        var configDomainService = new ConfigurationDomainService();
        var zkDomainService = new ZookeeperDomainService();

        // Create API handlers
        var serverApi = new ServerApi(zkDomainService, configDomainService);
        var nodeApi = new NodeApi(zkDomainService);
        var configApi = new ConfigApi(configDomainService);
        var terminalApi = new TerminalApi(zkDomainService, configDomainService);
        var logApi = new LogApi();

        // Start HTTP server
        PrettyZkWebServer server = new PrettyZkWebServer(
                serverApi, nodeApi, configApi, terminalApi, logApi
        );
        server.start(host, port);
        port = server.getPort();

        if (webMode) {
            // Web deployment mode: logs URL, externally accessible
            LOG.info("=============================================");
            LOG.info("  PrettyZk Web Server started");
            LOG.info("  Listen on http://{}:{}", host, port);
            LOG.info("=============================================");
        } else {
            // Desktop mode: print URL for Tauri/Electron shell
            LOG.info("PrettyZk started on http://127.0.0.1:{}", port);
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
