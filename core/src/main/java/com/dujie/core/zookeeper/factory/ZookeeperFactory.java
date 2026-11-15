package com.dujie.core.zookeeper.factory;

import com.dujie.client.curator.CuratorZookeeperConnectionFactory;
import com.dujie.client.zknative.NativeZookeeperConnection;
import com.dujie.core.configuration.entity.ServerConfiguration;
import com.dujie.core.configuration.value.SSHTunnelConfiguration;
import com.dujie.core.zookeeper.entity.SSHTunnel;
import com.dujie.core.zookeeper.entity.Terminal;
import com.dujie.core.zookeeper.entity.Zookeeper;
import com.dujie.model.connection.ZookeeperConnection;
import com.dujie.model.connection.ZookeeperParams;
import com.dujie.model.listener.ServerListener;
import com.dujie.model.listener.ZookeeperNodeListener;
import com.dujie.model.util.StringWriter;
import com.dujie.client.terminal.ZkCommandDispatcher;

import java.util.List;

public class ZookeeperFactory {

    public Zookeeper create(ServerConfiguration serverConfig,
                            List<ZookeeperNodeListener> nodeListeners,
                            List<ServerListener> serverListeners) {
        SSHTunnel tunnel = null;
        if (serverConfig.getSshTunnelEnabled() && serverConfig.getSshTunnel() != null) {
            final SSHTunnelConfiguration tunnelConfig = serverConfig.getSshTunnel();
            tunnel = SSHTunnel.builder()
                    .localhost(tunnelConfig.getLocalhost())
                    .localPort(tunnelConfig.getLocalPort())
                    .sshUsername(tunnelConfig.getSshUsername())
                    .sshPassword(tunnelConfig.getSshPassword())
                    .sshKeyFilePath(tunnelConfig.getSshKeyFilePath())
                    .sshHost(tunnelConfig.getSshHost())
                    .sshPort(tunnelConfig.getSshPort())
                    .remoteHost(tunnelConfig.getRemoteHost())
                    .remotePort(tunnelConfig.getRemotePort())
                    .build();
        }

        String zkVersion = serverConfig.getZkVersion() != null ? serverConfig.getZkVersion() : "auto";
        var params = ZookeeperParams.builder()
                .id(serverConfig.getId())
                .url(serverConfig.getConnectionTo())
                .aclList(serverConfig.getAclList())
                .maxRetries(serverConfig.getConnectionConfiguration().getMaxRetries())
                .connectionTimeout(serverConfig.getConnectionConfiguration().getConnectionTimeout())
                .retryIntervalTime(serverConfig.getConnectionConfiguration().getRetryIntervalTime())
                .sessionTimeout(serverConfig.getConnectionConfiguration().getSessionTimeout())
                .build();

        // Choose connection implementation based on ZK version
        return new Zookeeper(
                serverConfig.getId(),
                serverConfig.getConnectionTo(),
                () -> createConnection(zkVersion, params, serverListeners, serverConfig),
                tunnel,
                nodeListeners,
                serverListeners
        );
    }

    private ZookeeperConnection createConnection(String zkVersion, ZookeeperParams params,
                                                  List<ServerListener> serverListeners,
                                                  ServerConfiguration serverConfig) {
        return switch (zkVersion) {
            case "3.4" -> new NativeZookeeperConnection(
                    params.getId(),
                    params.getUrl(),
                    params.getSessionTimeout(),
                    params.getConnectionTimeout()
            );
            default -> {
                // "auto", "3.5", "3.6+" all use Curator 5.x by default
                var factory = new CuratorZookeeperConnectionFactory();
                yield factory.createAsync(params, serverListeners);
            }
        };
    }

    public Terminal createTerminal(String id, String url, StringWriter writer) throws Exception {
        writer.write("connecting to " + url + "...\n");
        var factory = new CuratorZookeeperConnectionFactory();
        var params = ZookeeperParams.builder()
                .id(id)
                .url(url)
                .aclList(List.of())
                .build();
        var connection = factory.create(params);
        var zk = connection.getClient().getZookeeperClient().getZooKeeper();
        writer.write("connect success \n");
        var dispatcher = new ZkCommandDispatcher(zk, writer);
        return new Terminal(id, url, connection, dispatcher);
    }
}
