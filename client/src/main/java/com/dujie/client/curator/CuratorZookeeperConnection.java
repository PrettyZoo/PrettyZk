package com.dujie.client.curator;

import com.dujie.model.connection.ZookeeperConnection;
import com.dujie.model.listener.ZookeeperNodeListener;
import com.dujie.model.node.NodeMode;
import com.dujie.model.node.ZkNode;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CreateBuilder;
import org.apache.curator.framework.api.DeleteBuilder;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheStorage;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

public class CuratorZookeeperConnection implements ZookeeperConnection<CuratorFramework> {

    private static final Logger log = LoggerFactory.getLogger(CuratorZookeeperConnection.class);

    private final String id;

    private final CuratorFramework curatorFramework;

    private final CuratorFramework cacheClient;

    private final CuratorCache cache;

    private final CountDownLatch treeReady = new CountDownLatch(1);

    private AtomicBoolean isSync = new AtomicBoolean(false);

    public CuratorZookeeperConnection(String id, CuratorFramework curatorFramework) {
        this(id, curatorFramework, null);
    }

    public CuratorZookeeperConnection(String id, CuratorFramework curatorFramework,
                                      CuratorFramework cacheClient) {
        this.id = id;
        this.curatorFramework = curatorFramework;
        this.cacheClient = cacheClient;
        this.cache = cacheClient == null ? null : CuratorCache.builder(cacheClient, "/")
                .withStorage(CuratorCacheStorage.dataNotCached())
                .build();
    }

    @Override
    public void create(String path, String data, boolean recursive, NodeMode mode) throws Exception {
        final CreateBuilder createBuilder = getClient().create();
        final CreateMode createMode = CreateMode.valueOf(mode.name());
        if (recursive) {
            createBuilder.creatingParentsIfNeeded()
                    .withMode(createMode)
                    .forPath(path, data.getBytes());
        } else {
            createBuilder.withMode(createMode)
                    .forPath(path, data.getBytes());
        }
    }

    @Override
    public void delete(String path, boolean recursive) throws Exception {
        final DeleteBuilder deleteBuilder = getClient().delete();
        if (recursive) {
            deleteBuilder.deletingChildrenIfNeeded().forPath(path);
        } else {
            deleteBuilder.forPath(path);
        }
    }

    @Override
    public void deleteAsync(List<String> pathList) throws Exception {
        for (String s : pathList) {
            getClient()
                    .delete()
                    .deletingChildrenIfNeeded()
                    .inBackground()
                    .forPath(s);
        }
    }

    @Override
    public Stat setData(String path, String data) throws Exception {
        return getClient().setData().forPath(path, data.getBytes());
    }

    @Override
    public void close() {
        if (cache != null) {
            cache.close();
        }
        if (cacheClient != null) {
            cacheClient.close();
        }
        curatorFramework.close();
        isSync.set(false);
    }

    @Override
    public CuratorFramework getClient() {
        return this.curatorFramework;
    }

    @Override
    public void sync(List<ZookeeperNodeListener> listeners) {
        if (cache == null) {
            return;
        }
        final String server = cacheClient.getZookeeperClient().getCurrentConnectionString();
        if (!isSync.get()) {
            log.debug("begin to sync tree node from {}", server);
            cache.listenable().addListener(new CuratorCacheHandler(id, server, listeners, treeReady));
            try {
                cache.start();
                isSync.set(true);
            } catch (Exception e) {
                log.error("sync zookeeper node error", e);
                cache.close();
            }
        } else {
            log.info("ignore sync operation, because of {} [{}] has been sync", server, getId());
        }
    }

    @Override
    public List<String> getChildren(String path) throws Exception {
        return curatorFramework.getChildren().forPath(path);
    }

    @Override
    public ZkNode getNode(String path) throws Exception {
        ZkNode node = new ZkNode();
        node.setPath(path);
        Stat stat = new Stat();
        byte[] data = curatorFramework.getData().storingStatIn(stat).forPath(path);
        node.setData(data == null ? "" : new String(data, StandardCharsets.UTF_8));
        node.setDataBytes(data == null ? new byte[0] : data);
        node.setStat(stat);
        return node;
    }

    @Override
    public int getChildCount(String path) {
        if (cache == null) {
            return -1;
        }
        return cache.get(path)
                .map(ChildData::getStat)
                .map(Stat::getNumChildren)
                .orElse(-1);
    }

    @Override
    public boolean hasCache() {
        return cache != null;
    }

    @Override
    public List<String> searchPaths(Pattern pattern, int limit) {
        if (cache == null) {
            return null;
        }
        try {
            if (!treeReady.await(500, TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return cache.stream()
                .map(ChildData::getPath)
                .filter(path -> pattern.matcher(path).find())
                .limit(limit)
                .toList();
    }

    @Override
    public String getId() {
        return this.id;
    }
}
