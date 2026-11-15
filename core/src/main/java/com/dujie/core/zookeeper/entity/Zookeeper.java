package com.dujie.core.zookeeper.entity;

import com.dujie.model.connection.ZookeeperConnection;
import com.dujie.model.listener.ServerListener;
import com.dujie.model.listener.ZookeeperNodeListener;
import com.dujie.model.node.NodeMode;
import com.dujie.model.node.ZkNode;
import lombok.Getter;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class Zookeeper {

    private String id;

    @Getter
    private final String url;

    @Getter
    private final ZookeeperConnection connection;

    private final SSHTunnel sshTunnel;

    private List<ZookeeperNodeListener> nodeListeners = List.of();

    private List<ServerListener> serverListeners = List.of();

    public Zookeeper(String id,
                     String url,
                     Supplier<ZookeeperConnection> connectionSupplier,
                     SSHTunnel sshTunnel,
                     List<ZookeeperNodeListener> nodeListeners,
                     List<ServerListener> serverListeners) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(url);
        Objects.requireNonNull(connectionSupplier);
        this.id = id;
        this.url = url;
        this.sshTunnel = sshTunnel;

        if (sshTunnel != null) {
            sshTunnel.createAsync();
            sshTunnel.blockUntilConnected();
        }
        this.connection = connectionSupplier.get();
        this.nodeListeners = nodeListeners;
        this.serverListeners = serverListeners;
    }

    public void disconnect() {
        connection.close();
        if (sshTunnel != null) {
            sshTunnel.close();
        }
        serverListeners.forEach(l -> l.onClose(this.id));
    }

    public void sync() {
        connection.sync(nodeListeners);
    }

    public Stat set(String path, String data) throws Exception {
        return connection.setData(path, data);
    }

    public void delete(String path) throws Exception {
        connection.delete(path, true);
    }

    public void deleteAsync(List<String> pathList) throws Exception {
        connection.deleteAsync(pathList);
    }

    public void create(String path, String data, NodeMode mode) throws Exception {
        connection.create(path, data, true, mode);
    }

    /**
     * Get children paths of a given path.
     */
    public int getChildCount(String path) {
        return connection.getChildCount(path);
    }

    public List<String> getChildren(String path) throws Exception {
        CuratorFramework client = (CuratorFramework) connection.getClient();
        try {
            return client.getChildren().forPath(path);
        } catch (Exception e) {
            // If we can't get children, return empty list (handles ACL/permission issues)
            return List.of();
        }
    }

    /**
     * Get node data and stat for a given path.
     */
    public ZkNode getNode(String path) throws Exception {
        CuratorFramework client = (CuratorFramework) connection.getClient();
        ZkNode node = new ZkNode();
        node.setPath(path);
        try {
            Stat stat = new Stat();
            byte[] dataBytes = client.getData().storingStatIn(stat).forPath(path);
            node.setData(dataBytes != null ? new String(dataBytes, StandardCharsets.UTF_8) : "");
            node.setDataBytes(dataBytes);
            node.setStat(stat);
        } catch (Exception e) {
            // If we can't read data, return node without data/stat (handles ACL/permission issues)
            node.setData("");
            node.setDataBytes(new byte[0]);
        }
        return node;
    }

    /**
     * Search nodes by keyword recursively from the root.
     */
    public SearchResult search(String keyword) throws Exception {
        CuratorFramework client = (CuratorFramework) connection.getClient();
        List<ZkNode> results = new java.util.ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean[] truncated = {false};
        searchRecursive(client, "/", keyword.toLowerCase(), results, deadline, truncated);
        return new SearchResult(results, truncated[0]);
    }

    private void searchRecursive(CuratorFramework client, String path, String keyword,
                                  List<ZkNode> results, long deadline,
                                  boolean[] truncated) throws Exception {
        if (results.size() >= 100 || System.nanoTime() > deadline) {
            truncated[0] = true;
            return;
        }
        if (path.toLowerCase().contains(keyword)) {
            try {
                results.add(getNode(path));
            } catch (Exception ignored) {
                // skip nodes that fail to read (e.g. ACL denied)
            }
        }
        try {
            List<String> children = client.getChildren().forPath(path);
            for (String child : children) {
                String childPath = "/".equals(path) ? "/" + child : path + "/" + child;
                searchRecursive(client, childPath, keyword, results, deadline, truncated);
            }
        } catch (Exception ignored) {
            // skip nodes that fail to list (e.g. ACL denied, no permission on sub-tree)
        }
    }
}
