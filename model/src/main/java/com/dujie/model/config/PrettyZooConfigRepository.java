package com.dujie.model.config;

import com.dujie.model.config.model.ConfigData;

import java.io.InputStream;
import java.io.OutputStream;

public interface PrettyZooConfigRepository {

    String CONFIG_PATH = System.getProperty("user.home") + "/.prettyZoo/prettyZoo.cfg";

    String THEME_DEFAULT = "default";

    ConfigData get();

    void save(ConfigData config);

    void importConfig(InputStream stream);

    void exportConfig(OutputStream targetStream);
}
