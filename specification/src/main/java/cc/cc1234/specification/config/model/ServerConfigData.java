package cc.cc1234.specification.config.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
public class ServerConfigData {

    /**
     * nullable
     */
    private String id;

    private String host;

    private Integer port;

    private String alias;

    private int connectTimes = 0;

    private List<String> aclList = new ArrayList<>();

    private Boolean sshTunnelEnabled = false;

    private Boolean enableConnectionAdvanceConfiguration = false;

    private Optional<SSHTunnelConfigData> sshTunnelConfig = Optional.empty();

    private ConnectionConfigData connectionConfig = new ConnectionConfigData();

    /**
     * ZooKeeper client version for compatibility:
     * - "auto" (default): Curator 5.x
     * - "3.4": Native ZooKeeper 3.4.x compatible
     * - "3.5": Curator 5.x with backward-compatible settings
     */
    private String zkVersion = "auto";

}
