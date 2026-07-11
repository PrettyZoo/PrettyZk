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
        int port = findFreePort();
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    try { port = Integer.parseInt(args[++i]); }
                    catch (NumberFormatException e) { /* ignore */ }
                }
            }
        }

        LOG.info("Starting PrettyZk...");
        var configDomainService = new ConfigurationDomainService();
        var zkDomainService = new ZookeeperDomainService();

        var serverApi = new ServerApi(zkDomainService, configDomainService);
        var nodeApi = new NodeApi(zkDomainService, configDomainService);
        var configApi = new ConfigApi(configDomainService);
        var terminalApi = new TerminalApi(zkDomainService, configDomainService);
        var logApi = new LogApi();

        PrettyZkWebServer server = new PrettyZkWebServer(
                serverApi, nodeApi, configApi, terminalApi, logApi
        );
        server.start("127.0.0.1", port);
        port = server.getPort();

        LOG.info("PrettyZk desktop app started on http://127.0.0.1:{}", port);
        server.await();
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return 8080;
        }
    }
}
