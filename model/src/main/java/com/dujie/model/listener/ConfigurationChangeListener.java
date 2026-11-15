package com.dujie.model.listener;

import com.dujie.model.config.model.ServerConfigData;

import java.util.List;
import java.util.Locale;

public interface ConfigurationChangeListener {

    default void onLocaleChange(Locale locale) {

    }

    default void onServerAdd(ServerConfigData serverConfig) {

    }

    default void onServerRemove(ServerConfigData serverConfig) {

    }

    default void onServerChange(ServerConfigData newValue) {

    }

    default void onReload(List<ServerConfigData> configs) {

    }

}
