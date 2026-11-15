package com.dujie.core.zookeeper.entity;

import com.dujie.model.connection.ZookeeperConnection;
import com.dujie.model.listener.ServerListener;
import com.dujie.model.listener.ZookeeperNodeListener;
import com.dujie.model.node.NodeMode;
import com.dujie.model.node.ZkNode;
import lombok.Getter;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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
        Pattern pattern = buildPattern(keyword);
        if (connection.hasCache()) {
            List<String> cachedPaths = connection.searchPaths(pattern, 100);
            if (cachedPaths == null) {
                // The initial tree snapshot is still being built. Do a fast
                // two-level scan so the user gets useful partial results
                // instead of waiting ~35s for a 50k-node cache.
                return searchShallow(client, pattern, 100);
            }
            for (String path : cachedPaths) {
                try {
                    results.add(getNode(path));
                } catch (Exception ignored) {
                    // skip nodes that fail to read (e.g. ACL denied)
                }
            }
            return new SearchResult(results, cachedPaths.size() >= 100);
        }
        searchRecursive(client, "/", pattern, results, deadline, truncated);
        return new SearchResult(results, truncated[0]);
    }

    private SearchResult searchShallow(CuratorFramework client, Pattern pattern, int limit)
            throws Exception {
        List<ZkNode> nodes = new java.util.ArrayList<>();
        String connectString = client.getZookeeperClient().getCurrentConnectionString();
        java.util.concurrent.CountDownLatch connected = new java.util.concurrent.CountDownLatch(1);
        ZooKeeper searchClient = new ZooKeeper(connectString, 15_000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        try {
            if (!connected.await(3, TimeUnit.SECONDS)) {
                return new SearchResult(nodes, true);
            }
            List<String> rootChildren = searchClient.getChildren("/", false);
            List<String> directories = new java.util.ArrayList<>();
            for (String name : rootChildren) {
                String path = "/" + name;
                if (pattern.matcher(path).find()) {
                    addNode(searchClient, nodes, path, limit);
                }
                directories.add(path);
            }
            for (String directory : directories) {
                if (nodes.size() >= limit) {
                    break;
                }
                try {
                    for (String name : searchClient.getChildren(directory, false)) {
                        String path = directory + "/" + name;
                        if (pattern.matcher(path).find()) {
                            addNode(searchClient, nodes, path, limit);
                        }
                        if (nodes.size() >= limit) {
                            break;
                        }
                    }
                } catch (Exception ignored) {
                    // skip subtrees that fail to list
                }
            }
        } finally {
            searchClient.close();
        }
        return new SearchResult(nodes, true);
    }

    private void addNode(ZooKeeper zk, List<ZkNode> nodes, String path, int limit) {
        if (nodes.size() >= limit) {
            return;
        }
        try {
            Stat stat = new Stat();
            byte[] data = zk.getData(path, false, stat);
            ZkNode node = new ZkNode();
            node.setPath(path);
            node.setData(data == null ? "" : new String(data, StandardCharsets.UTF_8));
            node.setDataBytes(data == null ? new byte[0] : data);
            node.setStat(stat);
            nodes.add(node);
        } catch (Exception ignored) {
            // skip nodes that fail to read
        }
    }

    private static Pattern buildPattern(String keyword) {
        String value = keyword == null ? "" : keyword.toLowerCase();
        if (value.indexOf('%') < 0 && value.indexOf('*') < 0) {
            return Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE);
        }
        StringBuilder regex = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '%' || c == '*') {
                regex.append(".*");
            } else if (".\\+?^$()[]{}|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private void searchRecursive(CuratorFramework client, String path, Pattern pattern,
                                  List<ZkNode> results, long deadline,
                                  boolean[] truncated) throws Exception {
        if (results.size() >= 100 || System.nanoTime() > deadline) {
            truncated[0] = true;
            return;
        }
        if (pattern.matcher(path).find()) {
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
                searchRecursive(client, childPath, pattern, results, deadline, truncated);
            }
        } catch (Exception ignored) {
            // skip nodes that fail to list (e.g. ACL denied, no permission on sub-tree)
        }
    }
}
