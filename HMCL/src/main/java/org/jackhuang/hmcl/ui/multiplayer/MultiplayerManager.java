/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.multiplayer;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import javafx.application.Platform;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Lang.wrap;
import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 * Cato Management.
 */
public final class MultiplayerManager {
    static final String HIPER_VERSION = "1.2.2";
    private static final String HIPER_DOWNLOAD_URL = "https://gitcode.net/to/hiper/-/raw/master/";
    private static final String HIPER_PACKAGES_URL = HIPER_DOWNLOAD_URL + "packages.sha1";
    private static final Path HIPER_CONFIG_PATH = Metadata.HMCL_DIRECTORY.resolve("hiper.yml");
    private static final String HIPER_PATH = getHiperPath();
    public static final int HIPER_AGREEMENT_VERSION = 2;
    private static final String REMOTE_ADDRESS = "127.0.0.1";
    private static final String LOCAL_ADDRESS = "0.0.0.0";

    private static CompletableFuture<Map<String, String>> HASH;

    private MultiplayerManager() {
    }

    private static CompletableFuture<Map<String, String>> getPackagesHash() {
        FXUtils.checkFxUserThread();
        if (HASH == null) {
            HASH = CompletableFuture.supplyAsync(wrap(() -> {
                String hashList = HttpRequest.GET(HIPER_PACKAGES_URL).getString();
                Map<String, String> hashes = new HashMap<>();
                for (String line : hashList.split("\n")) {
                    String[] items = line.trim().split(" {2}");
                    if (items.length == 2 && items[0].length() == 40) {
                        hashes.put(items[1], items[0]);
                    } else {
                        LOG.warning("Failed to parse Hiper packages.sha1 file, line: " + line);
                    }
                }
                return hashes;
            }));
        }
        return HASH;
    }

    public static Task<Void> downloadHiper() {
        return Task.fromCompletableFuture(getPackagesHash()).thenComposeAsync(packagesHash ->
                new FileDownloadTask(
                        NetworkUtils.toURL(HIPER_DOWNLOAD_URL + getHiperFileName()),
                        getHiperExecutable().toFile(),
                        packagesHash.get(getHiperFileName()) == null ? null : new FileDownloadTask.IntegrityCheck("SHA-1", packagesHash.get(getHiperFileName()))
                ).thenRunAsync(() -> {
                    if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                        Set<PosixFilePermission> perm = Files.getPosixFilePermissions(getHiperExecutable());
                        perm.add(PosixFilePermission.OWNER_EXECUTE);
                        Files.setPosixFilePermissions(getHiperExecutable(), perm);
                    }
                }));
    }

    public static Path getHiperExecutable() {
        return Metadata.HMCL_DIRECTORY.resolve("libraries").resolve(HIPER_PATH);
    }

    private static CompletableFuture<HiperSession> startHiper(String token, State state) {
        return getPackagesHash().thenApplyAsync(wrap(packagesHash -> {
            Path exe = getHiperExecutable();
            if (!Files.isRegularFile(exe)) {
                throw new HiperNotExistsException(exe);
            }

            try {
                String hash = packagesHash.get(getHiperFileName());
                if (hash != null) {
                    ChecksumMismatchException.verifyChecksum(exe, "SHA-1", hash);
                }
            } catch (IOException e) {
                Files.deleteIfExists(exe);
                throw e;
            }

            String[] commands = StringUtils.isBlank(token)
                    ? new String[]{exe.toString()}
                    : new String[]{exe.toString(), "-auth.token", token};
            Process process = new ProcessBuilder()
                    .command(commands)
                    .start();

            return new HiperSession(state, process, Arrays.asList(commands));
        }));
    }

    public static CompletableFuture<HiperSession> joinSession(String token, String peer, Mode mode, int remotePort, int localPort, JoinSessionHandler handler) throws IncompatibleHiperVersionException {
        LOG.info(String.format("Joining session (token=%s,peer=%s,mode=%s,remotePort=%d,localPort=%d)", token, peer, mode, remotePort, localPort));

        return startHiper(token, State.SLAVE).thenComposeAsync(wrap(session -> {
            CompletableFuture<HiperSession> future = new CompletableFuture<>();

            session.forwardPort(peer, LOCAL_ADDRESS, localPort, REMOTE_ADDRESS, remotePort, mode);

            Consumer<HiperExitEvent> onExit = event -> {
                boolean ready = session.isReady();
                switch (event.getExitCode()) {
                    case 1:
                        if (!ready) {
                            future.completeExceptionally(new HiperExitTimeoutException());
                        }
                        break;
                }
                future.completeExceptionally(new HiperExitException(event.getExitCode(), ready));
            };
            session.onExit.register(onExit);

            TimerTask peerConnectionTimeoutTask = Lang.setTimeout(() -> {
                future.completeExceptionally(new PeerConnectionTimeoutException());
                session.stop();
            }, 15 * 1000);

            session.onPeerConnected.register(event -> {
                peerConnectionTimeoutTask.cancel();

                MultiplayerClient client = new MultiplayerClient(session.getId(), localPort);
                session.addRelatedThread(client);
                session.setClient(client);

                TimerTask task = Lang.setTimeout(() -> {
                    Platform.runLater(() -> future.completeExceptionally(new JoinRequestTimeoutException()));
                    session.stop();
                }, 30 * 1000);

                client.onConnected().register(connectedEvent -> {
                    try {
                        int port = findAvailablePort();
                        session.forwardPort(peer, LOCAL_ADDRESS, port, REMOTE_ADDRESS, connectedEvent.getPort(), mode);
                        session.addRelatedThread(Lang.thread(new LocalServerBroadcaster(port, session), "LocalServerBroadcaster", true));
                        session.setName(connectedEvent.getSessionName());
                        client.setGamePort(port);
                        Platform.runLater(() -> {
                            session.onExit.unregister(onExit);
                            future.complete(session);
                        });
                    } catch (IOException e) {
                        session.stop();
                        Platform.runLater(() -> future.completeExceptionally(e));
                    }
                    task.cancel();
                });
                client.onKicked().register(kickedEvent -> {
                    session.stop();
                    task.cancel();
                    Platform.runLater(() -> {
                        future.completeExceptionally(new KickedException(kickedEvent.getReason()));
                    });
                });
                client.onDisconnected().register(disconnectedEvent -> {
                    Platform.runLater(() -> {
                        if (!client.isConnected()) {
                            // We fail to establish connection with server
                            future.completeExceptionally(new ConnectionErrorException());
                        }
                    });
                });
                client.onHandshake().register(handshakeEvent -> {
                    if (handler != null) {
                        handler.onWaitingForJoinResponse();
                    }
                });
                client.start();
            });

            return future;
        }));
    }

    public static CompletableFuture<HiperSession> createSession(String token, String sessionName, int gamePort, boolean allowAllJoinRequests) {
        LOG.info(String.format("Creating session (token=%s,sessionName=%s,gamePort=%d)", token, sessionName, gamePort));

        return startHiper(token, State.MASTER).thenComposeAsync(wrap(session -> {
            CompletableFuture<HiperSession> future = new CompletableFuture<>();

            MultiplayerServer server = new MultiplayerServer(sessionName, gamePort, allowAllJoinRequests);
            server.startServer();

            session.setName(sessionName);
            session.allowForwardingAddress(REMOTE_ADDRESS, server.getPort());
            session.allowForwardingAddress(REMOTE_ADDRESS, gamePort);
            session.showAllowedAddress();

            Consumer<HiperExitEvent> onExit = event -> {
                boolean ready = session.isReady();
                switch (event.getExitCode()) {
                    case 1:
                        if (!ready) {
                            future.completeExceptionally(new HiperExitTimeoutException());
                        }
                        break;
                }
                future.completeExceptionally(new HiperExitException(event.getExitCode(), ready));
            };

            session.onExit.register(onExit);
            session.setServer(server);
            session.addRelatedThread(server);

            TimerTask peerConnectionTimeoutTask = Lang.setTimeout(() -> {
                future.completeExceptionally(new PeerConnectionTimeoutException());
                session.stop();
            }, 15 * 1000);

            session.onPeerConnected.register(event -> {
                peerConnectionTimeoutTask.cancel();
                Platform.runLater(() -> {
                    session.onExit.unregister(onExit);
                    future.complete(session);
                });
            });

            return future;
        }));
    }

    public static final Pattern INVITATION_CODE_PATTERN = Pattern.compile("^(?<id>.*?)#(?<port>\\d{2,5})$");

    public static Invitation parseInvitationCode(String invitationCode) throws JsonParseException {
        Matcher matcher = INVITATION_CODE_PATTERN.matcher(invitationCode);
        if (!matcher.find()) throw new IllegalArgumentException("Invalid invitation code");
        return new Invitation(matcher.group("id"), Integer.parseInt(matcher.group("port")));
    }

    public static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String getHiperFileName() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            return "hiper.exe";
        } else {
            return "hiper";
        }
    }

    public static String getHiperPath() {
        String name = getHiperFileName();
        if (StringUtils.isBlank(name)) return "";
        return "hiper/hiper/" + MultiplayerManager.HIPER_VERSION + "/" + name;
    }

    public static class HiperSession extends ManagedProcess {
        private final EventManager<HiperExitEvent> onExit = new EventManager<>();
        private final EventManager<CatoIdEvent> onIdGenerated = new EventManager<>();
        private final EventManager<Event> onPeerConnected = new EventManager<>();

        private String name;
        private final State type;
        private String id;
        private boolean peerConnected = false;
        private MultiplayerClient client;
        private MultiplayerServer server;
        private final BufferedWriter writer;

        HiperSession(State type, Process process, List<String> commands) {
            super(process, commands);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            LOG.info("Started hiper with command: " + new CommandBuilder().addAll(commands));

            this.type = type;
            addRelatedThread(Lang.thread(this::waitFor, "HiperExitWaiter", true));
            pumpInputStream(this::checkCatoLog);
            pumpErrorStream(this::checkCatoLog);

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        public synchronized MultiplayerClient getClient() {
            return client;
        }

        public synchronized HiperSession setClient(MultiplayerClient client) {
            this.client = client;
            return this;
        }

        public MultiplayerServer getServer() {
            return server;
        }

        public HiperSession setServer(MultiplayerServer server) {
            this.server = server;
            return this;
        }

        private void checkCatoLog(String log) {
            LOG.info("[Hiper] " + log);
            if (id == null) {
                Matcher matcher = TEMP_TOKEN_PATTERN.matcher(log);
                if (matcher.find()) {
                    id = matcher.group("id");
                    onIdGenerated.fireEvent(new CatoIdEvent(this, id));
                }
            }

            if (!peerConnected) {
                Matcher matcher = PEER_CONNECTED_PATTERN.matcher(log);
                if (matcher.find()) {
                    peerConnected = true;
                    onPeerConnected.fireEvent(new Event(this));
                }
            }
        }

        private void waitFor() {
            try {
                int exitCode = getProcess().waitFor();
                LOG.info("Hiper exited with exitcode " + exitCode);
                onExit.fireEvent(new HiperExitEvent(this, exitCode));
            } catch (InterruptedException e) {
                onExit.fireEvent(new HiperExitEvent(this, HiperExitEvent.EXIT_CODE_INTERRUPTED));
            } finally {
                try {
                    if (writer != null)
                        writer.close();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to close Hiper stdin writer", e);
                }
            }
            destroyRelatedThreads();
        }

        public boolean isReady() {
            return id != null;
        }

        public synchronized String getName() {
            return name;
        }

        public synchronized void setName(String name) {
            this.name = name;
        }

        public State getType() {
            return type;
        }

        @Nullable
        public String getId() {
            return id;
        }

        public String generateInvitationCode(int serverPort) {
            if (id == null) {
                throw new IllegalStateException("id not generated");
            }
            return id + "#" + serverPort;
        }

        public synchronized void invokeCommand(String command) throws IOException {
            LOG.info("Invoking hiper: " + command);
            writer.write(command);
            writer.newLine();
            writer.flush();
        }

        public void forwardPort(String peerId, String localAddress, int localPort, String remoteAddress, int remotePort, Mode mode) throws IOException {
            invokeCommand(String.format("net add %s %s:%d %s:%d %s", peerId, localAddress, localPort, remoteAddress, remotePort, mode.getName()));
        }

        public void allowForwardingAddress(String address, int port) throws IOException {
            invokeCommand(String.format("ufw net open %s:%d", address, port));
        }

        public void showAllowedAddress() throws IOException {
            invokeCommand("ufw net whitelist");
        }

        public EventManager<HiperExitEvent> onExit() {
            return onExit;
        }

        public EventManager<CatoIdEvent> onIdGenerated() {
            return onIdGenerated;
        }

        public EventManager<Event> onPeerConnected() {
            return onPeerConnected;
        }

        private static final Pattern TEMP_TOKEN_PATTERN = Pattern.compile("id\\((?<id>\\w+)\\)");
        private static final Pattern PEER_CONNECTED_PATTERN = Pattern.compile("Connected to main net");
        private static final Pattern LOG_PATTERN = Pattern.compile("(\\[\\d+])\\s+(\\w+)\\s+(\\w+-{0,1}\\w+):\\s(.*)");
    }

    public static class HiperExitEvent extends Event {
        private final int exitCode;

        public HiperExitEvent(Object source, int exitCode) {
            super(source);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }

        public static final int EXIT_CODE_INTERRUPTED = -1;
        public static final int EXIT_CODE_SESSION_EXPIRED = 10;
    }

    public static class CatoIdEvent extends Event {
        private final String id;

        public CatoIdEvent(Object source, String id) {
            super(source);
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    enum State {
        DISCONNECTED,
        CONNECTING,
        MASTER,
        SLAVE
    }

    public static class Invitation {
        private final String id;
        @SerializedName("p")
        private final int channelPort;

        public Invitation(String id, int channelPort) {
            this.id = id;
            this.channelPort = channelPort;
        }

        public String getId() {
            return id;
        }

        public int getChannelPort() {
            return channelPort;
        }
    }

    public interface JoinSessionHandler {
        void onWaitingForJoinResponse();
    }

    public static class IncompatibleHiperVersionException extends Exception {
        private final String expected;
        private final String actual;

        public IncompatibleHiperVersionException(String expected, String actual) {
            this.expected = expected;
            this.actual = actual;
        }

        public String getExpected() {
            return expected;
        }

        public String getActual() {
            return actual;
        }
    }

    public enum Mode {
        P2P,
        BRIDGE;

        String getName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static class HiperExitException extends RuntimeException {
        private final int exitCode;
        private final boolean ready;

        public HiperExitException(int exitCode, boolean ready) {
            this.exitCode = exitCode;
            this.ready = ready;
        }

        public int getExitCode() {
            return exitCode;
        }

        public boolean isReady() {
            return ready;
        }
    }

    public static class HiperExitTimeoutException extends RuntimeException {
    }

    public static class HiperSessionExpiredException extends RuntimeException {
    }

    public static class HiperAlreadyStartedException extends RuntimeException {
    }

    public static class JoinRequestTimeoutException extends RuntimeException {
    }

    public static class PeerConnectionTimeoutException extends RuntimeException {
    }

    public static class ConnectionErrorException extends RuntimeException {
    }

    public static class KickedException extends RuntimeException {
        private final String reason;

        public KickedException(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    public static class HiperNotExistsException extends RuntimeException {
        private final Path file;

        public HiperNotExistsException(Path file) {
            this.file = file;
        }

        public Path getFile() {
            return file;
        }
    }
}
