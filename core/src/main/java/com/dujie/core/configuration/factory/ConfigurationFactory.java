package com.dujie.core.configuration.factory;

import com.dujie.client.config.JsonPrettyZooConfigRepository;
import com.dujie.core.configuration.entity.Configuration;
import com.dujie.core.configuration.entity.ConnectionConfiguration;
import com.dujie.core.configuration.entity.ServerConfiguration;
import com.dujie.core.configuration.value.SSHTunnelConfiguration;
import com.dujie.model.config.PrettyZooConfigRepository;
import com.dujie.model.config.model.ConfigData;
import com.dujie.model.config.model.ConnectionConfigData;
import com.dujie.model.config.model.ServerConfigData;
import com.dujie.model.listener.ConfigurationChangeListener;

import java.util.List;
import java.util.stream.Collectors;

public class ConfigurationFactory {

    private PrettyZooConfigRepository prettyZooConfigRepository = new JsonPrettyZooConfigRepository();

    public Configuration create(List<ConfigurationChangeListener> listeners) {
        ConfigData configData = prettyZooConfigRepository.get();
        final List<ServerConfiguration> serverConfigurations = configData.getServers()
                .stream()
                .map(serverConfig -> {
                    var host = serverConfig.getHost();
                    var port = serverConfig.getPort();
                    SSHTunnelConfiguration tunnelConfiguration = sshTunnelConfiguration(serverConfig);
                    ConnectionConfiguration connectionConfiguration = connectionConfiguration(serverConfig);
                    return ServerConfiguration.builder()
                            .id(serverConfig.getId())
                            .alias(serverConfig.getAlias())
                            .host(host)
                            .port(port)
                            .aclList(serverConfig.getAclList())
                            .connectTimes(serverConfig.getConnectTimes())
                            .sshTunnelEnabled(serverConfig.getSshTunnelEnabled())
                            .sshTunnel(tunnelConfiguration)
                            .enableConnectionAdvanceConfiguration(
                                    serverConfig.getEnableConnectionAdvanceConfiguration())
                            .connectionConfiguration(connectionConfiguration)
                            .build();
                })
                .collect(Collectors.toList());
        var fontConfiguration = getOrDefaultFontConfiguration(configData.getFontConfig());
        var locale = configData.getLocalConfig().getLang().getLocale();
        var localeConfiguration = new Configuration.LocaleConfiguration(locale);
        return Configuration.builder()
                .fontConfiguration(fontConfiguration)
                .localeConfiguration(localeConfiguration)
                .configurationChangeListeners(listeners)
                .serverConfigurations(serverConfigurations)
                .theme(configData.getTheme())
                .build();
    }

    private static SSHTunnelConfiguration sshTunnelConfiguration(ServerConfigData serverConfig) {
        return serverConfig.getSshTunnelConfig()
                .map(tunnelConfig -> SSHTunnelConfiguration.builder()
                        .localhost(tunnelConfig.getLocalhost())
                        .localPort(tunnelConfig.getLocalPort())
                        .sshUsername(tunnelConfig.getSshUsername())
                        .sshPassword(tunnelConfig.getPassword())
                        .sshKeyFilePath(tunnelConfig.getSshKeyFilePath())
                        .sshHost(tunnelConfig.getSshHost())
                        .sshPort(tunnelConfig.getSshPort())
                        .remoteHost(tunnelConfig.getRemoteHost())
                        .remotePort(tunnelConfig.getRemotePort())
                        .build())
                .orElse(null);
    }

    private ConnectionConfiguration connectionConfiguration(ServerConfigData serverConfig) {
        var connectionConfiguration = new ConnectionConfiguration();
        ConnectionConfigData originAdvance = serverConfig.getConnectionConfig();
        connectionConfiguration.setRetryIntervalTime(originAdvance.getRetryIntervalTime());
        connectionConfiguration.setMaxRetries(originAdvance.getMaxRetries());
        connectionConfiguration.setSessionTimeout(originAdvance.getSessionTimeout());
        connectionConfiguration.setConnectionTimeout(originAdvance.getConnectionTimeout());
        return connectionConfiguration;
    }

    private Configuration.FontConfiguration getOrDefaultFontConfiguration(ConfigData.FontConfigData data) {
        if (data == null) {
            return new Configuration.FontConfiguration(14);
        } else {
            return new Configuration.FontConfiguration(data.getFontSize());
        }
    }
}
