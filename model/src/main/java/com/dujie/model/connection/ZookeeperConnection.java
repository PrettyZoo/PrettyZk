package com.dujie.model.connection;

import com.dujie.model.listener.ZookeeperNodeListener;
import com.dujie.model.node.NodeMode;
import com.dujie.model.node.ZkNode;
import org.apache.zookeeper.data.Stat;

import java.util.List;
import java.util.regex.Pattern;

public interface ZookeeperConnection<T> {

    void create(String path, String data, boolean recursive, NodeMode mode) throws Exception;

    void delete(String path, boolean recursive) throws Exception;

    void deleteAsync(List<String> pathList) throws Exception;

    Stat setData(String path, String data) throws Exception;

    void close();

    T getClient();

    List<String> getChildren(String path) throws Exception;

    ZkNode getNode(String path) throws Exception;

    void sync(List<ZookeeperNodeListener> listeners);

    /**
     * Number of cached children, or -1 when the implementation has no cache.
     */
    default int getChildCount(String path) {
        return -1;
    }

    /**
     * Search the cached tree structure. Returns null when the implementation
     * has no cache, so callers can fall back to a live traversal.
     */
    default List<String> searchPaths(Pattern pattern, int limit) {
        return null;
    }

    default boolean hasCache() {
        return false;
    }

    String getId();
}
