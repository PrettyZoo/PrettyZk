package com.dujie.app.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manual update check. This is only called when the user clicks the version
 * label; the app itself never phones home on startup.
 *
 * <p>GitHub's API is blocked or rate-limited in some networks, so a plain
 * releases/tags Atom feed is used as a fallback.</p>
 */
public final class VersionChecker {

    private static final String API_LATEST =
            "https://api.github.com/repos/PrettyZoo/PrettyZk/releases/latest";
    private static final String RELEASES_FEED =
            "https://github.com/PrettyZoo/PrettyZk/releases.atom";
    private static final String TAGS_FEED =
            "https://github.com/PrettyZoo/PrettyZk/tags.atom";
    private static final String RELEASES_PAGE =
            "https://github.com/PrettyZoo/PrettyZk/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern ENTRY_TITLE = Pattern.compile(
            "<entry>.*?<title>([^<]+)</title>", Pattern.DOTALL);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VersionChecker() {
    }

    public static Map<String, Object> check() throws Exception {
        String latest;
        String url;
        String notes = "";
        try {
            JsonNode release = getJson(API_LATEST);
            latest = release.path("tag_name").asText("");
            url = release.path("html_url").asText(RELEASES_PAGE);
            notes = release.path("body").asText("");
        } catch (Exception apiFailure) {
            latest = firstEntryTitle(RELEASES_FEED);
            url = RELEASES_PAGE;
            if (latest.isEmpty()) {
                latest = firstEntryTitle(TAGS_FEED);
                if (!latest.isEmpty()) {
                    url = "https://github.com/PrettyZoo/PrettyZk/releases/tag/" + latest;
                }
            }
        }
        if (latest == null || latest.isBlank()) {
            throw new IllegalStateException("cannot determine the latest version");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", Version.VERSION);
        result.put("latest", latest);
        result.put("updateAvailable", isNewer(latest, Version.VERSION));
        result.put("url", url);
        result.put("notes", notes);
        return result;
    }

    private static JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PrettyZk")
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub API returned HTTP " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    private static String firstEntryTitle(String feedUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "PrettyZk")
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "";
            }
            Matcher matcher = ENTRY_TITLE.matcher(response.body());
            return matcher.find() ? matcher.group(1).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isNewer(String remote, String local) {
        String[] remoteParts = normalize(remote).split("\\.");
        String[] localParts = normalize(local).split("\\.");
        int length = Math.max(remoteParts.length, localParts.length);
        for (int i = 0; i < length; i++) {
            int r = i < remoteParts.length ? parse(remoteParts[i]) : 0;
            int l = i < localParts.length ? parse(localParts[i]) : 0;
            if (r != l) {
                return r > l;
            }
        }
        return false;
    }

    private static String normalize(String version) {
        if (version == null || version.isBlank()) {
            return "0";
        }
        String value = version.trim();
        return value.startsWith("v") ? value.substring(1) : value;
    }

    private static int parse(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(part.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
