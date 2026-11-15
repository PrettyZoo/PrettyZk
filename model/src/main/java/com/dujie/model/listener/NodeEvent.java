package com.dujie.model.listener;

import com.dujie.model.node.ZkNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeEvent {

    private ZkNode node;

    private String server;

    private String id;

}
