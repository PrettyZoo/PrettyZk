package com.dujie.core.zookeeper.entity;

import com.dujie.model.connection.ZookeeperConnection;
import com.dujie.client.terminal.ZkCommandDispatcher;
import lombok.Getter;

public class Terminal {

    private String serverId;

    @Getter
    private String host;

    private ZookeeperConnection connection;

    private ZkCommandDispatcher commandDispatcher;

    public Terminal(String id,
                    String host,
                    ZookeeperConnection connection,
                    ZkCommandDispatcher commandDispatcher) {
        this.serverId = id;
        this.host = host;
        this.connection = connection;
        this.commandDispatcher = commandDispatcher;
    }

    public void close() {
        connection.close();
    }

    public void execute(String command) throws Exception {
        commandDispatcher.executeLine(command);
    }
}
