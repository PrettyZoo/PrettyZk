package cc.cc1234.zknative;

import cc.cc1234.specification.connection.ZookeeperConnection;
import cc.cc1234.specification.listener.ZookeeperNodeListener;
import cc.cc1234.specification.node.NodeMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.KeeperException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class NativeZookeeperConnection implements ZookeeperConnection<ZooKeeper> {

    private final String id;
    private final String connectString;
    private final int sessionTimeout;
    private ZooKeeper zk;
    private volatile boolean connected;

    public NativeZookeeperConnection(String id, String connectString,
                                     int sessionTimeout, int connectionTimeout) {
        this.id = id;
        this.connectString = connectString;
        this.sessionTimeout = sessionTimeout;
        connect(connectionTimeout);
    }

    private void connect(int connectionTimeout) {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            this.zk = new ZooKeeper(connectString, sessionTimeout, event -> {
                Watcher.Event.KeeperState state = event.getState();
                if (state == Watcher.Event.KeeperState.SyncConnected) {
                    connected = true;
                    latch.countDown();
                } else if (state == Watcher.Event.KeeperState.Expired
                        || state == Watcher.Event.KeeperState.Disconnected) {
                    connected = false;
                }
            });
            latch.await();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to " + connectString, e);
        }
    }

    private ArrayList<ACL> getAcl() {
        return ZooDefs.Ids.OPEN_ACL_UNSAFE;
    }

    private org.apache.zookeeper.CreateMode toCreateMode(NodeMode mode) {
        switch (mode) {
            case EPHEMERAL: return org.apache.zookeeper.CreateMode.EPHEMERAL;
            case PERSISTENT_SEQUENTIAL: return org.apache.zookeeper.CreateMode.PERSISTENT_SEQUENTIAL;
            case EPHEMERAL_SEQUENTIAL: return org.apache.zookeeper.CreateMode.EPHEMERAL_SEQUENTIAL;
            default: return org.apache.zookeeper.CreateMode.PERSISTENT;
        }
    }

    @Override
    public void create(String path, String data, boolean recursive, NodeMode mode) throws Exception {
        org.apache.zookeeper.CreateMode zkMode = toCreateMode(mode);
        byte[] bytes = data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (recursive) {
            String[] parts = path.split("/");
            StringBuilder current = new StringBuilder();
            for (String part : parts) {
                if (part.isEmpty()) continue;
                current.append("/").append(part);
                try {
                    if (zk.exists(current.toString(), false) == null) {
                        zk.create(current.toString(), new byte[0], getAcl(), org.apache.zookeeper.CreateMode.PERSISTENT);
                    }
                } catch (KeeperException.NodeExistsException e) { /* race condition - node already exists */ }
            }
        }
        try {
            zk.create(path, bytes, getAcl(), zkMode);
        } catch (KeeperException.NodeExistsException e) { /* race condition - node already exists */ }
    }

    @Override
    public void delete(String path, boolean recursive) throws Exception {
        if (recursive) {
            try {
                List<String> children = zk.getChildren(path, false);
                for (String child : children) {
                    String childPath = path.equals("/") ? "/" + child : path + "/" + child;
                    delete(childPath, true);
                }
            } catch (KeeperException.NoNodeException e) { /* race condition - node already deleted */ }
        }
        try {
            zk.delete(path, -1);
        } catch (KeeperException.NoNodeException e) { /* race condition - node already deleted */ }
    }

    @Override
    public void deleteAsync(List<String> pathList) throws Exception {
        for (String path : pathList) delete(path, true);
    }

    @Override
    public Stat setData(String path, String data) throws Exception {
        byte[] bytes = data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return zk.setData(path, bytes, -1);
    }

    @Override
    public void close() {
        if (zk != null) {
            try { zk.close(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        connected = false;
    }

    @Override
    public ZooKeeper getClient() { return zk; }

    @Override
    public void sync(List<ZookeeperNodeListener> listeners) {
        // No TreeCache in native mode
    }

    @Override
    public String getId() { return id; }
}
