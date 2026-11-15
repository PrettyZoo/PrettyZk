package com.dujie.client.terminal;

import com.dujie.model.util.StringWriter;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small, purpose-built ZooKeeper terminal command dispatcher.
 *
 * <p>This replaces the vendored Apache ZooKeeperMain. It intentionally supports
 * the commands a GUI console needs, without the interactive shell, history,
 * watcher printing and JLine machinery.</p>
 */
public class ZkCommandDispatcher {

    private final ZooKeeper zk;

    private final StringWriter out;

    public ZkCommandDispatcher(ZooKeeper zk, StringWriter out) {
        this.zk = zk;
        this.out = out;
    }

    public void executeLine(String line) throws Exception {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        List<String> args = tokenize(trimmed);
        if (args.isEmpty()) {
            return;
        }
        String command = args.get(0).toLowerCase(Locale.ROOT);
        try {
            switch (command) {
                case "help" -> help();
                case "ls" -> ls(args);
                case "get" -> get(args);
                case "set" -> set(args);
                case "create" -> create(args);
                case "delete" -> delete(args);
                case "deleteall" -> deleteAll(args);
                case "stat" -> stat(args);
                case "getacl" -> getAcl(args);
                case "setacl" -> setAcl(args);
                case "addauth" -> addAuth(args);
                case "sync" -> sync(args);
                case "quit", "exit" -> out.write("Use the terminal tab close button to disconnect.\n");
                default -> out.write("Unknown command: " + command + "\nType 'help' for supported commands.\n");
            }
        } catch (KeeperException e) {
            out.write("KeeperErrorCode = " + e.code() + " for " + e.getPath() + "\n");
        } catch (Exception e) {
            out.write("Error: " + e.getMessage() + "\n");
        }
    }

    private void help() throws Exception {
        out.write("""
                Commands:
                  ls [-R] <path>              list children
                  get <path>                  read data and stat
                  set <path> <data> [version] write data
                  create <path> <data> [mode] create a node
                                                mode: persistent | ephemeral |
                                                persistent_sequential | ephemeral_sequential
                  delete <path> [version]     delete a node
                  deleteall <path>            delete recursively
                  stat <path>                 show stat
                  getAcl <path>               show ACLs
                  setAcl <path> <acl>         ACLs are scheme:id:perms;...
                  addauth <scheme> <auth>     add authentication
                  sync <path>                 sync with the leader
                  quit | exit                 disconnect hint
                """);
    }

    private void ls(List<String> args) throws Exception {
        boolean recursive = args.size() > 1 && "-R".equalsIgnoreCase(args.get(1));
        String path = pathArg(recursive ? 2 : 1, args);
        if (recursive) {
            listRecursive(path);
        } else {
            out.write(zk.getChildren(path, false).toString() + "\n");
        }
    }

    private void listRecursive(String path) throws Exception {
        out.write(path + ":\n");
        List<String> children = zk.getChildren(path, false);
        out.write(children.toString() + "\n");
        for (String child : children) {
            listRecursive(path.equals("/") ? "/" + child : path + "/" + child);
        }
    }

    private void get(List<String> args) throws Exception {
        String path = pathArg(1, args);
        Stat stat = new Stat();
        byte[] data = zk.getData(path, false, stat);
        out.write(new String(data, StandardCharsets.UTF_8) + "\n");
        out.write(stat.toString() + "\n");
    }

    private void set(List<String> args) throws Exception {
        if (args.size() < 3) {
            out.write("Usage: set <path> <data> [version]\n");
            return;
        }
        String path = args.get(1);
        int version = args.size() > 3 ? Integer.parseInt(args.get(3)) : -1;
        Stat stat = zk.setData(path, args.get(2).getBytes(StandardCharsets.UTF_8), version);
        out.write(stat.toString() + "\n");
    }

    private void create(List<String> args) throws Exception {
        if (args.size() < 3) {
            out.write("Usage: create <path> <data> [mode]\n");
            return;
        }
        String path = args.get(1);
        byte[] data = args.get(2).getBytes(StandardCharsets.UTF_8);
        CreateMode mode = parseMode(args.size() > 3 ? args.get(3) : "persistent");
        String created = zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, mode);
        out.write("Created " + created + "\n");
    }

    private void delete(List<String> args) throws Exception {
        if (args.size() < 2) {
            out.write("Usage: delete <path> [version]\n");
            return;
        }
        int version = args.size() > 2 ? Integer.parseInt(args.get(2)) : -1;
        zk.delete(args.get(1), version);
        out.write("\n");
    }

    private void deleteAll(List<String> args) throws Exception {
        deleteRecursive(pathArg(1, args));
        out.write("\n");
    }

    private void deleteRecursive(String path) throws Exception {
        for (String child : zk.getChildren(path, false)) {
            deleteRecursive(path.equals("/") ? "/" + child : path + "/" + child);
        }
        if (!"/".equals(path)) {
            zk.delete(path, -1);
        }
    }

    private void stat(List<String> args) throws Exception {
        String path = pathArg(1, args);
        Stat stat = zk.exists(path, false);
        if (stat == null) {
            out.write("Node does not exist: " + path + "\n");
        } else {
            out.write(stat.toString() + "\n");
        }
    }

    private void getAcl(List<String> args) throws Exception {
        String path = pathArg(1, args);
        Stat stat = new Stat();
        List<ACL> acls = zk.getACL(path, stat);
        for (ACL acl : acls) {
            out.write(acl.toString() + "\n");
        }
    }

    private void setAcl(List<String> args) throws Exception {
        if (args.size() < 3) {
            out.write("Usage: setAcl <path> <scheme:id:perms;...>\n");
            return;
        }
        List<ACL> acls = new ArrayList<>();
        for (String entry : args.get(2).split(";")) {
            String[] parts = entry.split(":", 3);
            if (parts.length != 3) {
                out.write("Invalid ACL entry: " + entry + "\n");
                return;
            }
            acls.add(new ACL(parsePerms(parts[2]), new Id(parts[0], parts[1])));
        }
        zk.setACL(args.get(1), acls, -1);
        out.write("\n");
    }

    private void addAuth(List<String> args) throws Exception {
        if (args.size() < 3) {
            out.write("Usage: addauth <scheme> <auth>\n");
            return;
        }
        zk.addAuthInfo(args.get(1), args.get(2).getBytes(StandardCharsets.UTF_8));
        out.write("Authenticated with scheme " + args.get(1) + "\n");
    }

    private void sync(List<String> args) throws Exception {
        String path = pathArg(1, args);
        zk.sync(path, null, null);
        out.write("Sync requested for " + path + "\n");
    }

    private String pathArg(int index, List<String> args) {
        return args.size() > index ? args.get(index) : "/";
    }

    private CreateMode parseMode(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "ephemeral" -> CreateMode.EPHEMERAL;
            case "persistent_sequential" -> CreateMode.PERSISTENT_SEQUENTIAL;
            case "ephemeral_sequential" -> CreateMode.EPHEMERAL_SEQUENTIAL;
            default -> CreateMode.PERSISTENT;
        };
    }

    private int parsePerms(String value) {
        int perms = 0;
        for (char c : value.toCharArray()) {
            perms |= switch (c) {
                case 'c' -> ZooDefs.Perms.CREATE;
                case 'd' -> ZooDefs.Perms.DELETE;
                case 'r' -> ZooDefs.Perms.READ;
                case 'w' -> ZooDefs.Perms.WRITE;
                case 'a' -> ZooDefs.Perms.ADMIN;
                default -> 0;
            };
        }
        return perms;
    }

    private List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (char c : line.toCharArray()) {
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
