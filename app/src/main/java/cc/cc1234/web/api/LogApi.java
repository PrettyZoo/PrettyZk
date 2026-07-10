package cc.cc1234.web.api;

import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LogApi {

    private static final Logger LOG = LoggerFactory.getLogger(LogApi.class);

    public void onConnect(SseClient client) {
        var userHome = System.getProperty("user.home");
        var logPath = Paths.get(userHome + "/.prettyZoo/log/prettyZoo.log");

        // Send last 50 lines
        try {
            if (Files.exists(logPath)) {
                List<String> lines = Files.readAllLines(logPath, Charset.defaultCharset());
                int start = Math.max(0, lines.size() - 50);
                for (int i = start; i < lines.size(); i++) {
                    client.sendEvent("log", lines.get(i));
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to read log file", e);
        }

        // Tail new lines
        var tailerThread = new Thread(() -> {
            try {
                if (!Files.exists(logPath)) return;
                RandomAccessFile reader = new RandomAccessFile(logPath.toFile(), "r");
                reader.seek(reader.length()); // Start from end

                while (!Thread.currentThread().isInterrupted()) {
                    String line = reader.readLine();
                    if (line != null) {
                        try {
                            client.sendEvent("log", new String(line.getBytes("ISO-8859-1"), Charset.defaultCharset()));
                        } catch (Exception ignored) { break; }
                    } else {
                        try { Thread.sleep(500); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                reader.close();
            } catch (Exception e) {
                LOG.error("Log tailer error", e);
            }
        });
        tailerThread.setDaemon(true);
        tailerThread.start();

        client.onClose(() -> tailerThread.interrupt());
    }
}
