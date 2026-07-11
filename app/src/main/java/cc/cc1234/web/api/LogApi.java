package cc.cc1234.web.api;

import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LogApi {

    private static final Logger LOG = LoggerFactory.getLogger(LogApi.class);
    private static final int INITIAL_LINES = 50;
    private static final long POLL_INTERVAL_MS = 500;

    public void onConnect(SseClient client) {
        var userHome = System.getProperty("user.home");
        if (userHome == null) {
            LOG.warn("user.home not set, cannot tail logs");
            client.sendEvent("log", "Log tailer unavailable: user.home not set");
            client.close();
            return;
        }
        var logPath = Paths.get(userHome + "/.prettyZoo/log/prettyZoo.log");

        // Send last INITIAL_LINES lines
        try {
            if (Files.exists(logPath)) {
                List<String> lines = Files.readAllLines(logPath, Charset.defaultCharset());
                int start = Math.max(0, lines.size() - INITIAL_LINES);
                for (int i = start; i < lines.size(); i++) {
                    client.sendEvent("log", lines.get(i));
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to read log file", e);
        }

        // Tail new lines
        var tailerThread = new Thread(() -> {
            if (!Files.exists(logPath)) return;
            try (RandomAccessFile reader = new RandomAccessFile(logPath.toFile(), "r")) {
                reader.seek(reader.length()); // Start from end

                while (!Thread.currentThread().isInterrupted()) {
                    String line = reader.readLine();
                    if (line != null) {
                        try {
                            // readLine uses ISO-8859-1; re-encode to UTF-8 for correct chars
                            byte[] raw = line.getBytes(StandardCharsets.ISO_8859_1);
                            client.sendEvent("log", new String(raw, StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            LOG.debug("SSE send failed, stopping log tailer", e);
                            break;
                        }
                    } else {
                        try {
                            Thread.sleep(POLL_INTERVAL_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Log tailer error", e);
            }
        });
        tailerThread.setDaemon(true);
        tailerThread.start();

        client.onClose(() -> tailerThread.interrupt());
    }
}
