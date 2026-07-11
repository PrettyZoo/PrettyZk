package cc.cc1234.core.zookeeper.entity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class FourLetterCommand {

    private String host;
    private int port;

    public FourLetterCommand(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String request(String command) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port));
            try (OutputStream outputStream = socket.getOutputStream()) {
                outputStream.write(command.getBytes());
                outputStream.flush();
            }
            return readResponse(socket);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String readResponse(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            var builder = new StringBuilder();
            String str;
            while ((str = reader.readLine()) != null) {
                builder.append(str).append('\n');
            }
            return builder.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
