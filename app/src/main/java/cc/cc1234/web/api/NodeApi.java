package cc.cc1234.web.api;

import cc.cc1234.core.configuration.service.ConfigurationDomainService;
import cc.cc1234.core.zookeeper.service.ZookeeperDomainService;
import cc.cc1234.specification.node.NodeMode;
import cc.cc1234.specification.node.ZkNode;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class NodeApi {

    private static final Logger LOG = LoggerFactory.getLogger(NodeApi.class);
    private static final Set<String> ALLOWED_4LC = Set.of("ruok", "stat", "mntr", "conf", "srvr", "cons", "dump", "envi", "srst");

    private final ZookeeperDomainService zookeeperDomainService;
    private final ConfigurationDomainService configurationDomainService;

    public NodeApi(ZookeeperDomainService zookeeperDomainService,
                   ConfigurationDomainService configurationDomainService) {
        this.zookeeperDomainService = zookeeperDomainService;
        this.configurationDomainService = configurationDomainService;
    }

    /**
     * GET /api/nodes/{serverId}?path=/
     */
    public void listNodes(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        String path = ctx.queryParam("path");
        if (path == null || path.isBlank()) path = "/";
        if (path.contains("..")) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid path"));
            return;
        }
        try {
            ZkNode node = zookeeperDomainService.getNode(serverId, path);
            List<String> children = zookeeperDomainService.getChildren(serverId, path);
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

            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (String childName : children) {
                String childPath = "/".equals(path) ? "/" + childName : path + "/" + childName;
                Map<String, Object> childInfo = new LinkedHashMap<>();
                childInfo.put("name", childName);
                childInfo.put("path", childPath);
                childNodes.add(childInfo);
            }
            result.put("children", childNodes);
            ctx.json(result);
        } catch (Exception e) {
            LOG.error("Failed to list nodes for server {} at {}", serverId, path, e);
            ctx.status(500);
            ctx.json(Map.of("error", "Failed to list nodes"));
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
            ctx.json(List.of());
            return;
        }
        try {
            List<ZkNode> results = zookeeperDomainService.search(serverId, query);
            List<Map<String, Object>> vos = results.stream()
                    .map(n -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("path", n.getPath());
                        m.put("dataLength", n.getDataLength());
                        return m;
                    })
                    .collect(Collectors.toList());
            ctx.json(vos);
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
}
