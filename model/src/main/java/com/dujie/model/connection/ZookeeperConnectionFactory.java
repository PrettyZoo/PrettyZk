package com.dujie.model.connection;

import com.dujie.model.listener.ServerListener;

import java.util.List;

public interface ZookeeperConnectionFactory<T> {

    ZookeeperConnection<T> create(ZookeeperParams params);

    default ZookeeperConnection<T> createAsync(ZookeeperParams params, List<ServerListener> listener) {
        return null;
    }
}
