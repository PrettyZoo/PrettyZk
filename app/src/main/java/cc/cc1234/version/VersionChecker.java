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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class VersionChecker {

    private static final Logger logger = LoggerFactory.getLogger(VersionChecker.class);

    public static CompletableFuture<VersionResult> checkAsync() {
        URI uri = URI.create("https://api.github.com/repos/vran-dev/PrettyZoo/releases/latest");
        var request = HttpRequest.newBuilder(uri).build();
        var client = HttpClient.newHttpClient();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        final JsonMapper mapper = new JsonMapper();
                        final ObjectNode node = mapper.readValue(response.body(), ObjectNode.class);
                        final String latestVersion = node.get("tag_name").asText("");
                        final String features = node.get("body").asText("");
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
        final String[] arr = remoteVersion.split("v");
        String r = remoteVersion;
        if (arr.length == 2) {
            r = arr[1];
        }

        final String[] localVersionArr = Version.VERSION.split("\\.");
        final String[] remoteVersionArr = r.split("\\.");
        for (int i = 0; i < localVersionArr.length; i++) {
            try {
                final int localVersionSymbol = Integer.parseInt(localVersionArr[i]);
                final int remoteVersionSymbol = Integer.parseInt(remoteVersionArr[i]);
                if (localVersionSymbol < remoteVersionSymbol) {
                    return true;
                } else if (localVersionSymbol > remoteVersionSymbol) {
                    return false;
                }
            } catch (Exception e) {
                return true;
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
