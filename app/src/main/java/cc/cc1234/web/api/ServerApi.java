package cc.cc1234.web.api;

import cc.cc1234.core.configuration.entity.Configuration;
import cc.cc1234.core.configuration.entity.ConnectionConfiguration;
import cc.cc1234.core.configuration.entity.ServerConfiguration;
import cc.cc1234.core.configuration.service.ConfigurationDomainService;
import cc.cc1234.core.configuration.value.SSHTunnelConfiguration;
import cc.cc1234.core.zookeeper.service.ZookeeperDomainService;
import cc.cc1234.specification.listener.NodeEvent;
import cc.cc1234.specification.listener.ServerListener;
import cc.cc1234.specification.listener.ZookeeperNodeListener;
import cc.cc1234.web.NodeEventWsManager;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ServerApi {

    private static final Logger LOG = LoggerFactory.getLogger(ServerApi.class);

    private final ZookeeperDomainService zookeeperDomainService;
    private final ConfigurationDomainService configurationDomainService;

    public ServerApi(ZookeeperDomainService zookeeperDomainService,
                     ConfigurationDomainService configurationDomainService) {
        this.zookeeperDomainService = zookeeperDomainService;
        this.configurationDomainService = configurationDomainService;
    }

    public void listServers(Context ctx) {
        ensureConfigLoaded(ctx);
        var config = configurationDomainService.get().orElseThrow();
        List<ServerVO> servers = config.getServerConfigurations()
                .stream()
                .map(this::toServerVO)
                .collect(Collectors.toList());
        ctx.json(servers);
    }

    public void getServer(Context ctx) {
        ensureConfigLoaded(ctx);
        String id = ctx.pathParam("id");
        var opt = configurationDomainService.getById(id);
        if (opt.isEmpty()) {
            ctx.status(404);
            ctx.json(Map.of("error", "Server not found: " + id));
            return;
        }
        ctx.json(toServerVO(opt.get()));
    }

    public void saveServer(Context ctx) {
        ensureConfigLoaded(ctx);
        try {
            ServerForm form = ctx.bodyAsClass(ServerForm.class);
            SSHTunnelConfiguration tunnelConfig = null;
            if (form.sshEnabled != null && form.sshEnabled) {
                tunnelConfig = SSHTunnelConfiguration.builder()
                        .sshHost(form.sshHost != null ? form.sshHost : "")
                        .sshPort(form.sshPort != null ? form.sshPort : 22)
                        .sshUsername(form.sshUsername != null ? form.sshUsername : "")
                        .sshPassword(form.sshPassword != null ? form.sshPassword : "")
                        .sshKeyFilePath(form.sshKeyFilePath != null ? form.sshKeyFilePath : "")
                        .remoteHost(form.remoteHost != null ? form.remoteHost : "")
                        .remotePort(form.remotePort != null ? form.remotePort : 2181)
                        .localhost(form.zkHost != null ? form.zkHost : "127.0.0.1")
                        .localPort(form.zkPort != null ? form.zkPort : 2181)
                        .build();
            }

            ConnectionConfiguration advanceConfig = new ConnectionConfiguration();
            if (form.connectionTimeout != null) {
                advanceConfig.setConnectionTimeout(form.connectionTimeout);
            }
            if (form.sessionTimeout != null) {
                advanceConfig.setSessionTimeout(form.sessionTimeout);
            }
            if (form.maxRetries != null) {
                advanceConfig.setMaxRetries(form.maxRetries);
            }
            if (form.retryIntervalTime != null) {
                advanceConfig.setRetryIntervalTime(form.retryIntervalTime);
            }

            boolean isNew = form.id == null || form.id.isBlank();
            var serverConfig = ServerConfiguration.builder()
                    .id(isNew ? UUID.randomUUID().toString() : form.id)
                    .alias(form.zkAlias != null ? form.zkAlias : "")
                    .host(form.zkHost != null ? form.zkHost : "")
                    .port(form.zkPort != null ? form.zkPort : 2181)
                    .aclList(form.acl != null ?
                            new ArrayList<>(List.of(form.acl.split("\n"))) : List.of())
                    .sshTunnelEnabled(form.sshEnabled != null && form.sshEnabled)
                    .sshTunnel(tunnelConfig)
                    .enableConnectionAdvanceConfiguration(form.connectionTimeout != null)
                    .connectionConfiguration(advanceConfig)
                    .zkVersion(form.zkVersion != null ? form.zkVersion : "auto")
                    .build();
            configurationDomainService.save(serverConfig);
            ctx.json(Map.of("id", serverConfig.getId()));
        } catch (Exception e) {
            LOG.error("Failed to save server", e);
            ctx.status(400);
            ctx.json(Map.of("error", e.getMessage()));
        }
    }

    public void deleteServer(Context ctx) {
        ensureConfigLoaded(ctx);
        String id = ctx.pathParam("id");
        try {
            configurationDomainService.deleteServerConfiguration(id);
            ctx.json(Map.of("deleted", id));
        } catch (Exception e) {
            ctx.status(400);
            ctx.json(Map.of("error", e.getMessage()));
        }
    }

    public void connect(Context ctx, NodeEventWsManager wsManager) {
        ensureConfigLoaded(ctx);
        String id = ctx.pathParam("id");
        var serverOpt = configurationDomainService.getById(id);
        if (serverOpt.isEmpty()) {
            ctx.status(404);
            ctx.json(Map.of("error", "Server not found"));
            return;
        }
        try {
            var serverConfig = serverOpt.get();
            List<ZookeeperNodeListener> nodeListeners = List.of(
                new ZookeeperNodeListener() {
                    @Override
                    public void onNodeUpdate(NodeEvent event) {
                        wsManager.broadcast(id, "updated", event);
                    }
                    @Override
                    public void onNodeDelete(NodeEvent event) {
                        wsManager.broadcast(id, "deleted", event);
                    }
                    @Override
                    public void onNodeAdd(NodeEvent event) {
                        wsManager.broadcast(id, "added", event);
                    }
                    @Override
                    public void syncCompleted(String server) {
                    }
                    @Override
                    public void disConnect(String server) {
                    }
                    @Override
                    public void reconnected(String server) {
                    }
                }
            );
            List<ServerListener> serverListeners = List.of();
            zookeeperDomainService.connect(serverConfig, nodeListeners, serverListeners);
            configurationDomainService.incrementConnectTimes(id);
            ctx.json(Map.of("status", "connected"));
        } catch (Exception e) {
            LOG.error("Failed to connect server: {}", id, e);
            ctx.status(500);
            ctx.json(Map.of("error", e.getMessage()));
        }
    }

    public void disconnect(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            zookeeperDomainService.disconnect(id);
            ctx.json(Map.of("status", "disconnected"));
        } catch (Exception e) {
            ctx.status(500);
            ctx.json(Map.of("error", e.getMessage()));
        }
    }

    private ServerVO toServerVO(ServerConfiguration s) {
        var vo = new ServerVO();
        vo.id = s.getId();
        vo.alias = s.getAlias();
        vo.host = s.getHost();
        vo.port = s.getPort();
        vo.acl = String.join("\n", s.getAclList());
        vo.sshEnabled = s.getSshTunnelEnabled();
        if (s.getSshTunnel() != null) {
            vo.sshHost = s.getSshTunnel().getSshHost();
            vo.sshPort = s.getSshTunnel().getSshPort();
            vo.sshUsername = s.getSshTunnel().getSshUsername();
            vo.remoteHost = s.getSshTunnel().getRemoteHost();
            vo.remotePort = s.getSshTunnel().getRemotePort();
        }
        if (s.getConnectionConfiguration() != null) {
            vo.connectionTimeout = s.getConnectionConfiguration().getConnectionTimeout();
            vo.sessionTimeout = s.getConnectionConfiguration().getSessionTimeout();
            vo.maxRetries = s.getConnectionConfiguration().getMaxRetries();
            vo.retryIntervalTime = s.getConnectionConfiguration().getRetryIntervalTime();
        }
        return vo;
    }

    private void ensureConfigLoaded(Context ctx) {
        if (configurationDomainService.get().isEmpty()) {
            configurationDomainService.load(List.of());
        }
    }

    public static class ServerVO {
        public String id;
        public String alias;
        public String host;
        public int port;
        public String acl;
        public boolean sshEnabled;
        public String sshHost;
        public int sshPort;
        public String sshUsername;
        public String remoteHost;
        public int remotePort;
        public Integer connectionTimeout;
        public Integer sessionTimeout;
        public Integer maxRetries;
        public Integer retryIntervalTime;
        public String zkVersion = "auto";
    }

    public static class ServerForm {
        public String id;
        public String zkAlias;
        public String zkHost;
        public Integer zkPort;
        public String acl;
        public Boolean sshEnabled;
        public String sshHost;
        public Integer sshPort;
        public String sshUsername;
        public String sshPassword;
        public String sshKeyFilePath;
        public String remoteHost;
        public Integer remotePort;
        public Integer connectionTimeout;
        public Integer sessionTimeout;
        public Integer maxRetries;
        public Integer retryIntervalTime;
        public String zkVersion = "auto";
    }
}
