package cc.cc1234.client.curator;

import org.apache.curator.framework.AuthInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ACLs {

    // Supports: "digest:user:password", "digest:user:password:cdrwa", "user:password"
    private static final Pattern DIGEST_PATTERN = Pattern.compile(
        "(?:(?<schema>\\w+):)?(?<auth>[^:]+:[^:]+)"
    );

    public static AuthInfo parseDigest(String acl) {
        if (acl == null || acl.isBlank()) {
            throw new IllegalArgumentException("ACL must not be empty");
        }
        final Matcher matcher = DIGEST_PATTERN.matcher(acl.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "invalid ACL format: '" + acl + "'. Expected: digest:user:password or user:password"
            );
        }
        String schema = matcher.group("schema");
        if (schema == null || schema.isBlank()) {
            schema = "digest";
        }
        String auth = matcher.group("auth");
        if (auth == null || auth.isBlank()) {
            throw new IllegalArgumentException("invalid ACL: missing auth info in '" + acl + "'");
        }
        return new AuthInfo(schema, auth.getBytes());
    }
}
