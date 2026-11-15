package com.dujie.core.configuration.entity;

import com.dujie.model.listener.ConfigurationChangeListener;
import lombok.*;

import java.util.*;
import java.util.stream.Collectors;

@Builder
@Getter
public class Configuration {

    @Singular
    private List<ServerConfiguration> serverConfigurations;

    @Singular
    private List<ConfigurationChangeListener> configurationChangeListeners;

    private FontConfiguration fontConfiguration;

    private LocaleConfiguration localeConfiguration;

    private String theme;

    public Configuration(List<ServerConfiguration> serverConfigs,
                         List<ConfigurationChangeListener> listeners,
                         FontConfiguration fontConfiguration,
                         LocaleConfiguration localeConfiguration,
                         String theme) {
        Objects.requireNonNull(listeners);
        Objects.requireNonNull(serverConfigs);
        this.serverConfigurations = serverConfigs;
        this.configurationChangeListeners = listeners;
        this.fontConfiguration = fontConfiguration;
        this.localeConfiguration = localeConfiguration;
        this.theme = theme;

    }

    public void add(ServerConfiguration serverConfiguration) {
        serverConfigurationPrecondition(serverConfiguration);
        serverConfigurations.stream()
                .filter(s -> java.util.Objects.equals(s.getId(), serverConfiguration.getId()))
                .findFirst()
                .ifPresent(s -> {
                    throw new IllegalStateException(serverConfiguration.getLabel() + " exists");
                });
        var copiedServers = new ArrayList<>(this.serverConfigurations);
        copiedServers.add(serverConfiguration);
        this.serverConfigurations = copiedServers;
    }

    public void update(ServerConfiguration serverConfiguration) {
        serverConfigurationPrecondition(serverConfiguration);
        ServerConfiguration server = serverConfigurations.stream()
                .filter(s -> java.util.Objects.equals(s.getId(), serverConfiguration.getId()))
                .findFirst()
                .orElseThrow();
        server.update(serverConfiguration);
    }

    public void updateFont(FontConfiguration fontConfiguration) {
        this.fontConfiguration = fontConfiguration;
    }

    public void updateLocale(LocaleConfiguration localeConfiguration) {
        Objects.requireNonNull(localeConfiguration.getLocale());
        this.localeConfiguration = localeConfiguration;
        configurationChangeListeners.forEach(listener ->
                listener.onLocaleChange(this.localeConfiguration.getLocale()));
    }

    public Optional<ServerConfiguration> getById(String id) {
        return serverConfigurations.stream().filter(s -> java.util.Objects.equals(s.getId(), id)).findFirst();
    }

    public Boolean existsById(String id) {
        return getById(id).isPresent();
    }

    public void deleteById(String id) {
        final ServerConfiguration existsServer = getById(id).orElseThrow();
        List<ServerConfiguration> configurations = serverConfigurations.stream()
                .filter(s -> !java.util.Objects.equals(s.getId(), id))
                .collect(Collectors.toList());
        this.serverConfigurations = configurations;
    }

    public void incrementConnectTimes(String id) {
        serverConfigurations.stream()
                .filter(config -> config.getId().equals(id))
                .forEach(ServerConfiguration::incrementConnectTimes);
    }

    public void changeTheme(String theme) {
        this.theme = theme;
    }

    private void serverConfigurationPrecondition(ServerConfiguration serverConfig) {
        Objects.requireNonNull(serverConfig);
        Objects.requireNonNull(serverConfig.getHost());
        Objects.requireNonNull(serverConfig.getPort());
        Objects.requireNonNull(serverConfig.getSshTunnelEnabled());
        if (serverConfig.getSshTunnelEnabled() && serverConfig.getSshTunnel() == null) {
            throw new IllegalStateException("add SSHTunnel before save");
        }
        final String alias = serverConfig.getAlias();
        if (alias != null && !alias.isEmpty() && alias.isBlank()) {
            throw new IllegalStateException("Alias must not be all blank");
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FontConfiguration {

        private Integer size;

        public void checkIsValid() {
            if (size == null) {
                throw new IllegalArgumentException("font size is invalid");
            }
            if (size < 8 || size > 50) {
                throw new IllegalArgumentException("font size is invalid");
            }
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LocaleConfiguration {

        private Locale locale;

    }
}
