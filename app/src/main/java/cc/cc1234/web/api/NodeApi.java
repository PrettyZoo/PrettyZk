package cc.cc1234.web.api;

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

    private final ZookeeperDomainService zookeeperDomainService;

    public NodeApi(ZookeeperDomainService zookeeperDomainService) {
        this.zookeeperDomainService = zookeeperDomainService;
    }

    /**
     * GET /api/nodes/{serverId}?path=/
     */
    public void listNodes(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        String path = ctx.queryParam("path");
        if (path == null || path.isBlank()) path = "/";
        try {
            // Lazy load: get node and children directly without full TreeCache sync
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

            // Lazy load: only return child names/paths, no per-child getNode() call
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
            ctx.json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/nodes/{serverId}
     */
    public void createNode(Context ctx) {
        String serverId = ctx.pathParam("serverId");
        try {
            CreateNodeForm form = ctx.bodyAsClass(CreateNodeForm.class);
            String parentPath = form.path != null ? form.path : "/";
            String fullPath = parentPath.endsWith("/") ? parentPath + form.name : parentPath + "/" + form.name;
            NodeMode mode = form.mode != null ? NodeMode.valueOf(form.mode.toUpperCase()) : NodeMode.PERSISTENT;
            zookeeperDomainService.create(serverId, fullPath, form.data != null ? form.data : "", mode);
            ctx.json(Map.of("path", fullPath));
        } catch (Exception e) {
            LOG.error("Failed to create node", e);
            ctx.status(500);
            ctx.json(Map.of("error", e.getMessage()));
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
            ctx.json(Map.of("error", e.getMessage()));
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
            } catch (Exception ignored) {
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
            ctx.json(Map.of("error", e.getMessage()));
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
            ctx.json(Map.of("error", e.getMessage()));
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
