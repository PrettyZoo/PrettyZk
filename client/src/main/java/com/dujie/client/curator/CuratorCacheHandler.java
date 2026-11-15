package com.dujie.client.curator;

import com.dujie.model.listener.NodeEvent;
import com.dujie.model.listener.ZookeeperNodeListener;
import com.dujie.model.node.ZkNode;
import com.dujie.model.util.PathUtils;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * CuratorCache event adapter. Initial replay events are suppressed; the UI
 * loads nodes on demand and is told when the initial snapshot is ready.
 */
public class CuratorCacheHandler implements CuratorCacheListener {

    private final String id;

    private final String server;

    private final List<ZookeeperNodeListener> listeners;

    private final CountDownLatch readyLatch;

    private volatile boolean initialized;

    public CuratorCacheHandler(String id,
                               String server,
                               List<ZookeeperNodeListener> listeners,
                               CountDownLatch readyLatch) {
        this.id = id;
        this.server = server;
        this.listeners = listeners;
        this.readyLatch = readyLatch;
    }

    @Override
    public void event(Type type, ChildData oldData, ChildData data) {
        if (!initialized) {
            return;
        }
        ChildData current = data != null ? data : oldData;
        if (current == null) {
            return;
        }
        String path = current.getPath();
        ZkNode node = new ZkNode();
        node.setPath(path);
        node.setName(PathUtils.getLastPath(path));
        node.setStat(current.getStat() == null ? new Stat() : current.getStat());
        byte[] bytes = current.getData();
        node.setData(bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8));
        node.setDataBytes(bytes == null ? new byte[0] : bytes);
        NodeEvent event = new NodeEvent(node, server, id);
        switch (type) {
            case NODE_CREATED -> listeners.forEach(listener -> listener.onNodeAdd(event));
            case NODE_CHANGED -> listeners.forEach(listener -> listener.onNodeUpdate(event));
            case NODE_DELETED -> listeners.forEach(listener -> listener.onNodeDelete(event));
            default -> {
            }
        }
    }

    @Override
    public void initialized() {
        initialized = true;
        readyLatch.countDown();
        listeners.forEach(listener -> listener.syncCompleted(server));
    }
}
