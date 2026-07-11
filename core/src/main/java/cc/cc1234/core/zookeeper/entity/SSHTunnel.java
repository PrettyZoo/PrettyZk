package cc.cc1234.core.zookeeper.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Parameters;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Builder
@Getter
@Slf4j
public class SSHTunnel {

    private String localhost;
    private int localPort;
    private String sshHost;
    private int sshPort;
    private String sshUsername;
    private String sshPassword;
    private String remoteHost;
    private int remotePort;
    private String sshKeyFilePath;

    // Cross-thread access: written in createAsync thread, read in close/isConnected
    private volatile SSHClient sshClient;
    private volatile ServerSocket proxySocket;

    // Latch signaled when the port-forwarder starts listening
    private final CountDownLatch connectedLatch = new CountDownLatch(1);
    // Holds any exception from the forwarder thread
    private final AtomicReference<IOException> forwarderError = new AtomicReference<>();

    public void createAsync() {
        try {
            sshClient = new SSHClient();
            log.warn("SSH host key verification disabled - accepting all host keys. Consider enabling known_hosts for production.");
            sshClient.addHostKeyVerifier(new PromiscuousVerifier());
            sshClient.connect(getSshHost(), getSshPort());

            if (getSshPassword() != null && !getSshPassword().isBlank()) {
                log.info("use password auth to create ssh-tunnel");
                sshClient.authPassword(getSshUsername(), getSshPassword());
            } else if (getSshKeyFilePath() != null && !getSshKeyFilePath().isBlank()) {
                log.info("use key file {} auth to create ssh-tunnel", getSshKeyFilePath());
                sshClient.authPublickey(getSshUsername(), getSshKeyFilePath());
            } else {
                log.info("use default key auth to create ssh-tunnel");
                sshClient.authPublickey(getSshUsername());
            }

            proxySocket = new ServerSocket();
            proxySocket.setReuseAddress(true);
            proxySocket.bind(new InetSocketAddress(localhost, localPort));

            new Thread(() -> {
                try {
                    Parameters param = new Parameters(localhost, localPort, remoteHost, remotePort);
                    // Signal that we're about to start listening
                    connectedLatch.countDown();
                    sshClient.newLocalPortForwarder(param, proxySocket).listen();
                } catch (IOException e) {
                    forwarderError.set(e);
                    connectedLatch.countDown(); // unblock waiters even on failure
                    log.error("SSH port forwarder failed", e);
                }
            }, "ssh-tunnel-forwarder").start();
        } catch (IOException e) {
            if (e.getClass().getSimpleName().contains("Timeout")) {
                throw new IllegalStateException("SSH connect error by timeout: " + sshHost, e);
            }
            if (e.getClass().getSimpleName().contains("UnknownHost")) {
                throw new IllegalStateException("SSH connect error by Unknown host " + sshHost, e);
            }
            log.error("create ssh-tunnel failed", e);
            this.close();
            throw new IllegalStateException("create ssh-tunnel failed", e);
        }
    }

    public void blockUntilConnected() {
        try {
            if (!connectedLatch.await(7, TimeUnit.SECONDS)) {
                this.close();
                throw new IllegalStateException("connect SSH Tunnel timed out after 7s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.close();
            throw new IllegalStateException("Interrupted while waiting for SSH tunnel", e);
        }

        IOException err = forwarderError.get();
        if (err != null) {
            this.close();
            throw new IllegalStateException("SSH port forwarder failed: " + err.getMessage(), err);
        }

        if (!isConnected()) {
            this.close();
            throw new IllegalStateException("connect SSH Tunnel failed");
        }
    }

    public boolean isConnected() {
        SSHClient client = sshClient;
        return client != null && client.isConnected();
    }

    public void close() {
        if (proxySocket != null) {
            try {
                proxySocket.close();
            } catch (IOException e) {
                log.debug("Error closing proxy socket", e);
            }
        }

        if (sshClient != null) {
            try {
                sshClient.close();
            } catch (IOException e) {
                log.debug("Error closing SSH client", e);
            }
        }
    }
}
