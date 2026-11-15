package com.dujie.core.zookeeper.entity;

import com.dujie.model.node.ZkNode;

import java.util.List;

public class SearchResult {

    private final List<ZkNode> nodes;

    private final boolean truncated;

    public SearchResult(List<ZkNode> nodes, boolean truncated) {
        this.nodes = nodes;
        this.truncated = truncated;
    }

    public List<ZkNode> getNodes() {
        return nodes;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
