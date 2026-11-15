package com.dujie.core.configuration.service;

import com.dujie.client.config.JsonPrettyZooConfigRepository;
import com.dujie.core.configuration.entity.Configuration;
import com.dujie.core.configuration.entity.ServerConfiguration;
import com.dujie.core.configuration.factory.ConfigurationFactory;
import com.dujie.core.configuration.factory.ConfigurationMapper;
import com.dujie.model.config.PrettyZooConfigRepository;
import com.dujie.model.listener.ConfigurationChangeListener;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class ConfigurationDomainService {

    private static final Cache<Configuration> configurationCache = new Cache<>();

    private PrettyZooConfigRepository prettyZooConfigRepository = new JsonPrettyZooConfigRepository();

    public Configuration load(List<ConfigurationChangeListener> listeners) {
        final Configuration configuration = new ConfigurationFactory().create(listeners);
        configurationCache.setVal(configuration);
        prettyZooConfigRepository.save(ConfigurationMapper.toPersistModel(configuration));
        final var servers = configuration.getServerConfigurations()
                .stream()
                .map(ConfigurationMapper::toServerConfig)
                .toList();
        listeners.forEach(listener -> listener.onReload(servers));
        return configuration;
    }

    public Configuration load() {
        return new ConfigurationFactory().create(List.of());
    }

    public void save(ServerConfiguration serverConfig) {
        final Configuration configuration = get().orElseThrow();
        final boolean exists = getById(serverConfig.getId()).isPresent();
        if (exists) {
            configuration.update(serverConfig);
        } else {
            configuration.add(serverConfig);
        }
        prettyZooConfigRepository.save(ConfigurationMapper.toPersistModel(configuration));
        final var data = ConfigurationMapper.toServerConfig(serverConfig);
        if (exists) {
            configuration.getConfigurationChangeListeners().forEach(listener -> listener.onServerChange(data));
        } else {
            configuration.getConfigurationChangeListeners().forEach(listener -> listener.onServerAdd(data));
        }
    }

    public void save(Configuration.FontConfiguration fontConfiguration) {
        fontConfiguration.checkIsValid();
        var configuration = get().orElseThrow();
        configuration.updateFont(fontConfiguration);
        prettyZooConfigRepository.save(ConfigurationMapper.toPersistModel(configuration));
    }

    public void save(Configuration.LocaleConfiguration localeConfiguration) {
        Objects.requireNonNull(localeConfiguration);
        var configuration = get().orElseThrow();
        configuration.updateLocale(localeConfiguration);
        prettyZooConfigRepository.save(ConfigurationMapper.toPersistModel(configuration));
    }

    public void saveTheme(String theme) {
        var configuration = get().orElseThrow();
        configuration.changeTheme(theme);
        prettyZooConfigRepository.save(ConfigurationMapper.toPersistModel(configuration));
    }

    public Optional<Configuration> get() {
        return Optional.ofNullable(configurationCache.getVal());
    }

    public Optional<ServerConfiguration> getById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return get().orElseThrow()
                .getServerConfigurations()
                .stream()
                .filter(s -> Objects.equals(s.getId(), id))
                .findFirst();
    }

    public void deleteServerConfiguration(String id) {
        Objects.requireNonNull(configurationCache.getVal());
        final Configuration configuration = configurationCache.getVal();
        final ServerConfiguration removed = configuration.getById(id).orElseThrow();
        configuration.deleteById(id);
        prettyZooConfigRepository.save(ConfigurationMapper.toPersistModel(configuration));
        final var data = ConfigurationMapper.toServerConfig(removed);
        configuration.getConfigurationChangeListeners().forEach(listener -> listener.onServerRemove(data));
    }

    public void importConfig(File configFile) {
        try (var stream = Files.newInputStream(configFile.toPath(), StandardOpenOption.READ)) {
            prettyZooConfigRepository.importConfig(stream);
            final Configuration originConfiguration = configurationCache.getVal();
            load(originConfiguration.getConfigurationChangeListeners());
        } catch (Exception e) {
            log.error("import config failed", e);
            throw new IllegalStateException("import config failed", e);
        }
    }

    public void exportConfig(File dir) {
        try (var stream = Files.newOutputStream(dir.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            prettyZooConfigRepository.exportConfig(stream);
        } catch (IOException e) {
            log.error("export config failed", e);
            throw new IllegalStateException("export config failed", e);
        }
    }

    public void incrementConnectTimes(String id) {
        get().ifPresent(config -> {
            config.incrementConnectTimes(id);
            prettyZooConfigRepository.save(ConfigurationMapper.toPersistModel(config));
        });
    }

    private static class Cache<T> {

        private T val;

        public T getVal() {
            return val;
        }

        public void setVal(T val) {
            this.val = val;
        }

    }
}
