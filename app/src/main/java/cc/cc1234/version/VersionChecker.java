package cc.cc1234.version;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class VersionChecker {

    private static final Logger logger = LoggerFactory.getLogger(VersionChecker.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final JsonMapper JSON_MAPPER = new JsonMapper();

    public static CompletableFuture<VersionResult> checkAsync() {
        URI uri = URI.create("https://api.github.com/repos/PrettyZoo/PrettyZk/releases/latest");
        var request = HttpRequest.newBuilder(uri).build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        final ObjectNode node = JSON_MAPPER.readValue(response.body(), ObjectNode.class);
                        final String latestVersion = node.has("tag_name")
                                ? node.get("tag_name").asText("") : "";
                        final String features = node.has("body")
                                ? node.get("body").asText("") : "";
                        boolean hasNew = isLargerThanCurrent(latestVersion);
                        return new VersionResult(hasNew, latestVersion, features);
                    } catch (Exception e) {
                        logger.error("Failed to parse version check response", e);
                        return new VersionResult(false, Version.VERSION, "");
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Version check failed", ex);
                    return new VersionResult(false, Version.VERSION, "");
                });
    }

    private static boolean isLargerThanCurrent(String remoteVersion) {
        if (remoteVersion == null || remoteVersion.isBlank()) {
            return false;
        }
        // Strip optional leading "v" prefix (e.g., "v3.0.0" -> "3.0.0")
        String r = remoteVersion.startsWith("v") ? remoteVersion.substring(1) : remoteVersion;

        final String[] localVersionArr = Version.VERSION.split("\\.");
        final String[] remoteVersionArr = r.split("\\.");

        int maxLen = Math.max(localVersionArr.length, remoteVersionArr.length);
        for (int i = 0; i < maxLen; i++) {
            try {
                int local = i < localVersionArr.length
                        ? Integer.parseInt(localVersionArr[i].trim()) : 0;
                int remote = i < remoteVersionArr.length
                        ? Integer.parseInt(remoteVersionArr[i].trim()) : 0;
                if (local < remote) {
                    return true;
                } else if (local > remote) {
                    return false;
                }
            } catch (NumberFormatException e) {
                logger.debug("Non-numeric version segment at position {}, local='{}', remote='{}'",
                        i, i < localVersionArr.length ? localVersionArr[i] : "0",
                        i < remoteVersionArr.length ? remoteVersionArr[i] : "0");
                return false; // Don't alert on non-numeric versions
            }
        }
        return false;
    }

    public static class VersionResult {
        private final boolean hasNewVersion;
        private final String latestVersion;
        private final String features;

        public VersionResult(boolean hasNewVersion, String latestVersion, String features) {
            this.hasNewVersion = hasNewVersion;
            this.latestVersion = latestVersion;
            this.features = features;
        }

        public boolean hasNewVersion() { return hasNewVersion; }
        public String getLatestVersion() { return latestVersion; }
        public String getFeatures() { return features; }
    }
}
