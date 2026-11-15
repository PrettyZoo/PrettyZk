package com.dujie.model.connection;

import com.dujie.model.listener.ZookeeperNodeListener;
import com.dujie.model.node.NodeMode;
import org.apache.zookeeper.data.Stat;

import java.util.List;

public interface ZookeeperConnection<T> {

    void create(String path, String data, boolean recursive, NodeMode mode) throws Exception;

    void delete(String path, boolean recursive) throws Exception;

    void deleteAsync(List<String> pathList) throws Exception;

    Stat setData(String path, String data) throws Exception;

    void close();

    T getClient();

    void sync(List<ZookeeperNodeListener> listeners);

    String getId();
}
