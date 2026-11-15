package com.dujie.core.configuration.factory;

import com.dujie.core.configuration.entity.Configuration;
import com.dujie.core.configuration.entity.ConnectionConfiguration;
import com.dujie.core.configuration.entity.ServerConfiguration;
import com.dujie.core.configuration.value.SSHTunnelConfiguration;
import com.dujie.model.config.model.ConfigData;
import com.dujie.model.config.model.ConnectionConfigData;
import com.dujie.model.config.model.SSHTunnelConfigData;
import com.dujie.model.config.model.ServerConfigData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts the domain entity into the persisted configuration model.
 * Keeping this out of the entity avoids coupling domain objects to storage.
 */
public final class ConfigurationMapper {

    private ConfigurationMapper() {
    }

    public static ConfigData toPersistModel(Configuration configuration) {
        var configData = new ConfigData();
        List<ServerConfigData> servers = configuration.getServerConfigurations()
                .stream()
                .map(ConfigurationMapper::toServerConfig)
                .collect(Collectors.toList());
        var fontConfig = new ConfigData.FontConfigData(configuration.getFontConfiguration().getSize());
        var langConfig = new ConfigData.LocalConfigData(
                ConfigData.Lang.valueOf(configuration.getLocaleConfiguration().getLocale()));
        configData.setTheme(configuration.getTheme());
        configData.setServers(servers);
        configData.setFontConfig(fontConfig);
        configData.setLocalConfig(langConfig);
        return configData;
    }

    public static ServerConfigData toServerConfig(ServerConfiguration config) {
        SSHTunnelConfigData sshTunnelData = null;
        if (config.getSshTunnelEnabled()) {
            SSHTunnelConfiguration tunnelConfiguration = config.getSshTunnel();
            sshTunnelData = new SSHTunnelConfigData();
            sshTunnelData.setLocalhost(tunnelConfiguration.getLocalhost());
            sshTunnelData.setLocalPort(tunnelConfiguration.getLocalPort());
            sshTunnelData.setSshHost(tunnelConfiguration.getSshHost());
            sshTunnelData.setSshPort(tunnelConfiguration.getSshPort());
            sshTunnelData.setSshUsername(tunnelConfiguration.getSshUsername());
            sshTunnelData.setPassword(tunnelConfiguration.getSshPassword());
            sshTunnelData.setSshKeyFilePath(tunnelConfiguration.getSshKeyFilePath());
            sshTunnelData.setRemoteHost(tunnelConfiguration.getRemoteHost());
            sshTunnelData.setRemotePort(tunnelConfiguration.getRemotePort());
        }

        final ServerConfigData serverData = new ServerConfigData();
        serverData.setId(config.getId());
        serverData.setConnectTimes(config.getConnectTimes());
        serverData.setAclList(new ArrayList<>(config.getAclList()));
        serverData.setHost(config.getHost());
        serverData.setPort(config.getPort());
        serverData.setSshTunnelEnabled(config.getSshTunnelEnabled());
        serverData.setSshTunnelConfig(Optional.ofNullable(sshTunnelData));
        serverData.setAlias(config.getAlias());
        serverData.setEnableConnectionAdvanceConfiguration(config.getEnableConnectionAdvanceConfiguration());
        serverData.setZkVersion(config.getZkVersion());

        ConnectionConfigData advanceConfig = serverData.getConnectionConfig();
        ConnectionConfiguration inputAdvanceConfig = config.getConnectionConfiguration();
        advanceConfig.setConnectionTimeout(inputAdvanceConfig.getConnectionTimeout());
        advanceConfig.setSessionTimeout(inputAdvanceConfig.getSessionTimeout());
        advanceConfig.setMaxRetries(inputAdvanceConfig.getMaxRetries());
        advanceConfig.setRetryIntervalTime(inputAdvanceConfig.getRetryIntervalTime());
        return serverData;
    }
}
