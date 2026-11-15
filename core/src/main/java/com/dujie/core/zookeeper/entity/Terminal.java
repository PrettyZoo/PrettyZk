package com.dujie.core.zookeeper.entity;

import com.dujie.model.connection.ZookeeperConnection;
import com.dujie.client.zookeeper.ZooKeeperMain;
import lombok.Getter;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;

public class Terminal {

    private String serverId;

    @Getter
    private String host;

    private ZookeeperConnection connection;

    private ZooKeeperMain zooKeeperMain;

    public Terminal(String id,
                    String host,
                    ZookeeperConnection connection,
                    ZooKeeperMain zooKeeperMain) {
        this.serverId = id;
        this.host = host;
        this.connection = connection;
        this.zooKeeperMain = zooKeeperMain;
    }

    public void close() {
        connection.close();
    }

    public void execute(String command) throws InterruptedException, IOException, KeeperException {
        zooKeeperMain.executeLine(command);
    }
}
