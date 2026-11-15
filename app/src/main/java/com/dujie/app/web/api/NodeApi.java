package com.dujie.app.web.api;

import com.dujie.core.configuration.service.ConfigurationDomainService;
import com.dujie.core.zookeeper.service.ZookeeperDomainService;
import com.dujie.model.node.NodeMode;
import com.dujie.model.node.ZkNode;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class NodeApi {

    private static final Logger LOG = LoggerFactory.getLogger(NodeApi.class);
    private static final Set<String> ALLOWED_4LC = Set.of(
            "ruok", "stat", "mntr", "conf", "srvr", "cons", "dump", "envi", "srst");

    private final ZookeeperDomainService zookeeperDomainService;
    private final ConfigurationDomainService configurationDomainService;

    public NodeApi(ZookeeperDomainService zookeeperDomainService,
                   ConfigurationDomainService configurationDomainService) {
        this.zookeeperDomainService = zookeeperDomainService;
        this.configurationDomainService = configurationDomainService;
    }

    /**
     * GET /api/nodes/{serverId}/detail?path=/
     * Node data/stat only. The tree uses /children, so selecting a directory
     * with 50k children does not download the whole child list again.
     */
    public void getNodeDetail(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        String path = ctx.queryParam("path");
        if (path == null || path.isBlank()) {
            path = "/";
        }
        if (path.contains("..")) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid path"));
            return;
        }
        try {
            ZkNode node = zookeeperDomainService.getNode(serverId, path);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", node.getPath());
            result.put("data", node.getData());
            result.put("ephemeralOwner", node.getEphemeralOwner());
            result.put("dataLength", node.getDataLength());
            result.put("numChildren", node.getNumChildren());
            result.put("creationTime", node.getCtime());
            result.put("modifiedTime", node.getMtime());
            result.put("dataVersion", node.getVersion());
            result.put("ephemeral", node.getEphemeralOwner() != 0);
            ctx.json(result);
        } catch (Exception e) {
            LOG.error("Failed to get node detail for server {} at {}", serverId, path, e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to get node detail"));
        }
    }

    /**
     * GET /api/nodes/{serverId}/children?path=/
     * Lightweight tree expansion payload: only child names are returned.
     */
    public void listChildren(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        String path = ctx.queryParam("path");
        if (path == null || path.isBlank()) {
            path = "/";
        }
        if (path.contains("..")) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid path"));
            return;
        }
        try {
            List<String> names = zookeeperDomainService.getChildren(serverId, path);
            int total = names.size();
            int offset = parseBounded(ctx.queryParam("offset"), 0, 0, total);
            int limit = parseBounded(ctx.queryParam("limit"), 5000, 1, 20000);
            int end = Math.min(total, offset + limit);
            List<Map<String, Object>> children = new ArrayList<>();
            for (String name : names.subList(offset, end)) {
                String childPath = "/".equals(path) ? "/" + name : path + "/" + name;
                Map<String, Object> child = new LinkedHashMap<>();
                child.put("name", name);
                int childCount = zookeeperDomainService.getChildCount(serverId, childPath);
                if (childCount >= 0) {
                    child.put("numChildren", childCount);
                }
                children.add(child);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("total", total);
            result.put("offset", offset);
            result.put("limit", limit);
            result.put("hasMore", end < total);
            result.put("children", children);
            ctx.json(result);
        } catch (Exception e) {
            LOG.error("Failed to list children for server {} at {}", serverId, path, e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to list children"));
        }
    }

    /**
     * POST /api/nodes/{serverId}
     */
    public void createNode(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        try {
            CreateNodeForm form = ctx.bodyAsClass(CreateNodeForm.class);
            if (form.name == null || form.name.isBlank()) {
                ctx.status(400);
                ctx.json(Map.of("error", "Node name is required"));
                return;
            }
            if (form.name.contains("/") || form.name.contains("..")) {
                ctx.status(400);
                ctx.json(Map.of("error", "Invalid node name"));
                return;
            }
            String parentPath = form.path != null && !form.path.isBlank() ? form.path : "/";
            String fullPath = parentPath.endsWith("/") ? parentPath + form.name : parentPath + "/" + form.name;
            NodeMode mode = form.mode != null ? NodeMode.valueOf(form.mode.toUpperCase()) : NodeMode.PERSISTENT;
            zookeeperDomainService.create(serverId, fullPath, form.data != null ? form.data : "", mode);
            ctx.json(Map.of("path", fullPath));
        } catch (Exception e) {
            LOG.error("Failed to create node", e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to create node"));
        }
    }

    /**
     * PUT /api/nodes/{serverId}
     */
    public void updateNode(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        try {
            UpdateNodeForm form = ctx.bodyAsClass(UpdateNodeForm.class);
            zookeeperDomainService.setData(serverId, form.path, form.data);
            ctx.json(Map.of("path", form.path, "status", "updated"));
        } catch (Exception e) {
            LOG.error("Failed to update node", e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to update node"));
        }
    }

    /**
     * DELETE /api/nodes/{serverId}?path=/some/node
     */
    public void deleteNode(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        String path = ctx.queryParam("path");
        if (path == null || path.isBlank()) {
            try {
                DeleteNodeForm form = ctx.bodyAsClass(DeleteNodeForm.class);
                if (form.paths != null && !form.paths.isEmpty()) {
                    zookeeperDomainService.delete(serverId, form.paths);
                    ctx.json(Map.of("deleted", form.paths));
                    return;
                }
            } catch (Exception e) {
                LOG.debug("Failed to parse delete body as DeleteNodeForm", e);
            }
            ctx.status(400);
            ctx.json(Map.of("error", "path query parameter or paths in body required"));
            return;
        }
        try {
            zookeeperDomainService.delete(serverId, List.of(path));
            ctx.json(Map.of("deleted", path));
        } catch (Exception e) {
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to delete node"));
        }
    }

    /**
     * GET /api/nodes/{serverId}/search?q=keyword
     */
    public void searchNodes(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        String query = ctx.queryParam("q");
        if (query == null || query.isBlank()) {
            ctx.json(Map.of("nodes", List.of(), "truncated", false));
            return;
        }
        try {
            var result = zookeeperDomainService.search(serverId, query);
            List<Map<String, Object>> vos = result.getNodes().stream()
                    .map(n -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("path", n.getPath());
                        m.put("dataLength", n.getDataLength());
                        return m;
                    })
                    .collect(Collectors.toList());
            ctx.json(Map.of("nodes", vos, "truncated", result.isTruncated()));
        } catch (Exception e) {
            ctx.status(500);
            ctx.json(Map.of("error", "Search failed"));
        }
    }

    /**
     * POST /api/nodes/{serverId}/4lc
     */
    public void executeFourLetterCmd(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        try {
            String command;
            Object body = ctx.bodyAsClass(Object.class);
            if (body instanceof Map) {
                Object cmd = ((Map<?, ?>) body).get("command");
                if (cmd instanceof String s && !s.isBlank()) {
                    command = s.trim().toLowerCase();
                } else {
                    ctx.status(400);
                    ctx.json(Map.of("error", "command must be a non-empty string"));
                    return;
                }
            } else {
                ctx.status(400);
                ctx.json(Map.of("error", "Request body must be a JSON object"));
                return;
            }

            if (command.length() != 4) {
                ctx.status(400);
                ctx.json(Map.of("error", "command must be 4 characters"));
                return;
            }
            if (!ALLOWED_4LC.contains(command)) {
                ctx.status(400);
                ctx.json(Map.of("error", "Unknown or unsupported 4-letter command: " + command));
                return;
            }

            var serverConfig = configurationDomainService.getById(serverId).orElseThrow();
            String hostToConnect;
            if (serverConfig.getSshTunnelEnabled()) {
                // Connect via SSH tunnel's local forwarded address
                var sshTunnel = serverConfig.getSshTunnel();
                String remoteHost = sshTunnel.getRemoteHost() != null && !sshTunnel.getRemoteHost().isBlank()
                        ? sshTunnel.getRemoteHost() : "localhost";
                hostToConnect = remoteHost + ":" + serverConfig.getPort();
            } else {
                hostToConnect = serverConfig.getHost() + ":" + serverConfig.getPort();
            }
            String result = zookeeperDomainService.execute4LetterCommand(
                serverConfig.getId(), hostToConnect, command);
            ctx.json(Map.of("command", command, "result", result));
        } catch (Exception e) {
            LOG.error("Failed to execute 4LC for server {}", serverId, e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to execute command"));
        }
    }

    public static class CreateNodeForm {
        public String path;
        public String name;
        public String data;
        public String mode;
    }

    public static class UpdateNodeForm {
        public String path;
        public String data;
    }

    public static class DeleteNodeForm {
        public List<String> paths;
    }

    private static int parseBounded(String value, int fallback, int min, int max) {
        if (value == null || value.isBlank()) {
            return Math.min(fallback, max);
        }
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(min, Math.min(parsed, max));
        } catch (NumberFormatException e) {
            return Math.min(fallback, max);
        }
    }
}
