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
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.launch.StreamPump;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
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

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.wrap;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * Cato Management.
 */
public final class MultiplayerManager {
    static final String CATO_VERSION = "1.2.0-202112171527";
    private static final String CATO_DOWNLOAD_URL = "https://gitcode.net/huanghongxun1/ioi_bin/-/raw/c7b7d8c61b66c297a6460f744dc14487c3ec2289/client/";
    private static final String CATO_PATH = getCatoPath();
    public static final int CATO_AGREEMENT_VERSION = 2;
    private static final String REMOTE_ADDRESS = "127.0.0.1";
    private static final String LOCAL_ADDRESS = "0.0.0.0";

    private static final Map<String, String> HASH = mapOf(
            pair("cato-client-darwin-amd64", "03c22a68f37654fea60761a1fcb5deb122ea69b8"),
            pair("cato-client-darwin-arm64", "f79b80437a4b13dde72718dacc80c0469491ad48"),
            pair("cato-client-freebsd-amd64", "6b244259e3887f5878a87b0a3fcdd5eec9a90a46"),
            pair("cato-client-freebsd-arm64", "5529f6eacc5a47f9b6e41841531b5bbd911f74e5"),
            pair("cato-client-freebsd-arm7", "f4b32ecadf82538965f969306e783fa7a7ce86a9"),
            pair("cato-client-freebsd-i386", "24e27e63e9efe657de23f36f5217232d3c704de3"),
            pair("cato-client-js.wasm", "3f5d13513b97e750d6cabce9b0c418e7187ad5c7"),
            pair("cato-client-linux-amd64", "a3f6611ca9e602821faf909d9c6ec243236fed47"),
            pair("cato-client-linux-arm64", "d04602c1b041f727a2429530e102392f596cc458"),
            pair("cato-client-linux-arm7", "0924b8d71e62cc80ff7c09a6e1c6aa5b5ea9d77f"),
            pair("cato-client-linux-i386", "fa426104d4cb0f028bbde5b8e6dddfa9169befd3"),
            pair("cato-client-linux-mips", "1d8f2710df9e708ef9cc422336cf3fe9e48674e9"),
            pair("cato-client-linux-mips64", "73ceb1c4e5374766a358d09fcb82a799fb65e846"),
            pair("cato-client-linux-mips64le", "9d3af8504d049cb502b85fb0d87b9919881e029f"),
            pair("cato-client-linux-mipsle", "0e771bde5a16fb03c952c6de06e4a65efe23ce83"),
            pair("cato-client-linux-ppc64", "1199b67ec24afdb4dee2fe4e5f4eeeef8ac3e1e3"),
            pair("cato-client-linux-ppc64le", "107f6d23685eb98ea961c7e7556d1956c364da32"),
            pair("cato-client-openbsd-amd64", "606493c36521996b1ce41cd980a775db791b783f"),
            pair("cato-client-openbsd-arm64", "4c1b2d89e88669452d174607bc65ed3897420868"),
            pair("cato-client-openbsd-arm7", "a51860b41c4d3634d84fcec578f6ed15f8a49401"),
            pair("cato-client-openbsd-i386", "9ac8f86b7ceb15564424fbd578867fae93b5cb47"),
            pair("cato-client-windows-amd64.exe", "57c5166b4c02046ea73d0321481f379db8b103d3"),
            pair("cato-client-windows-arm64.exe", "d6b9025f1a2a1202256546cfb38ed7d863f5bb57"),
            pair("cato-client-windows-i386.exe", "45959f36c061ae7cd4294575053920fc43a85516")
    );

    private MultiplayerManager() {
    }

    public static Task<Void> downloadCato() {
        return new FileDownloadTask(
                NetworkUtils.toURL(CATO_DOWNLOAD_URL + getCatoFileName()),
                getCatoExecutable().toFile(),
                new FileDownloadTask.IntegrityCheck("SHA-1", HASH.get(getCatoFileName()))
        ).thenRunAsync(() -> {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                Set<PosixFilePermission> perm = Files.getPosixFilePermissions(getCatoExecutable());
                perm.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(getCatoExecutable(), perm);
            }
        });
    }

    public static Path getCatoExecutable() {
        return Metadata.HMCL_DIRECTORY.resolve("libraries").resolve(CATO_PATH);
    }

    private static CompletableFuture<CatoSession> startCato(String token, State state) {
        return CompletableFuture.completedFuture(null).thenApplyAsync(wrap(unused -> {
            Path exe = getCatoExecutable();
            if (!Files.isRegularFile(exe)) {
                throw new CatoNotExistsException(exe);
            }

            if (!isPortAvailable(3478)) {
                throw new CatoAlreadyStartedException();
            }

            try {
                ChecksumMismatchException.verifyChecksum(exe, "SHA-1", HASH.get(getCatoFileName()));
            } catch (IOException e) {
                Files.deleteIfExists(exe);
                throw e;
            }

            String[] commands = new String[]{exe.toString(), "--token", StringUtils.isBlank(token) ? "new" : token};
            Process process = new ProcessBuilder()
                    .command(commands)
                    .start();

            return new CatoSession(state, process, Arrays.asList(commands));
        }));
    }

    public static CompletableFuture<CatoSession> joinSession(String token, String peer, Mode mode, int remotePort, int localPort, JoinSessionHandler handler) throws IncompatibleCatoVersionException {
        LOG.info(String.format("Joining session (token=%s,peer=%s,mode=%s,remotePort=%d,localPort=%d)", token, peer, mode, remotePort, localPort));

        return startCato(token, State.SLAVE).thenComposeAsync(wrap(session -> {
            CompletableFuture<CatoSession> future = new CompletableFuture<>();

            session.forwardPort(peer, LOCAL_ADDRESS, localPort, REMOTE_ADDRESS, remotePort, mode);

            Consumer<CatoExitEvent> onExit = event -> {
                boolean ready = session.isReady();
                switch (event.getExitCode()) {
                    case 1:
                        if (!ready) {
                            future.completeExceptionally(new CatoExitTimeoutException());
                        }
                        break;
                }
                future.completeExceptionally(new CatoExitException(event.getExitCode(), ready));
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
                    future.completeExceptionally(new JoinRequestTimeoutException());
                    session.stop();
                }, 30 * 1000);

                client.onConnected().register(connectedEvent -> {
                    try {
                        int port = findAvailablePort();
                        session.forwardPort(peer, LOCAL_ADDRESS, port, REMOTE_ADDRESS, connectedEvent.getPort(), mode);
                        session.addRelatedThread(Lang.thread(new LocalServerBroadcaster(port, session), "LocalServerBroadcaster", true));
                        session.setName(connectedEvent.getSessionName());
                        client.setGamePort(port);
                        session.onExit.unregister(onExit);
                        future.complete(session);
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                        session.stop();
                    }
                    task.cancel();
                });
                client.onKicked().register(kickedEvent -> {
                    future.completeExceptionally(new KickedException(kickedEvent.getReason()));
                    session.stop();
                    task.cancel();
                });
                client.onDisconnected().register(disconnectedEvent -> {
                    if (!client.isConnected()) {
                        // We fail to establish connection with server
                        future.completeExceptionally(new ConnectionErrorException());
                    }
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

    public static CompletableFuture<CatoSession> createSession(String token, String sessionName, int gamePort, boolean allowAllJoinRequests) {
        LOG.info(String.format("Creating session (token=%s,sessionName=%s,gamePort=%d)", token, sessionName, gamePort));

        return startCato(token, State.MASTER).thenComposeAsync(wrap(session -> {
            CompletableFuture<CatoSession> future = new CompletableFuture<>();

            MultiplayerServer server = new MultiplayerServer(sessionName, gamePort, allowAllJoinRequests);
            server.startServer();

            session.setName(sessionName);
            session.allowForwardingAddress(REMOTE_ADDRESS, server.getPort());
            session.allowForwardingAddress(REMOTE_ADDRESS, gamePort);
            session.showAllowedAddress();

            Consumer<CatoExitEvent> onExit = event -> {
                boolean ready = session.isReady();
                switch (event.getExitCode()) {
                    case 1:
                        if (!ready) {
                            future.completeExceptionally(new CatoExitTimeoutException());
                        }
                        break;
                }
                future.completeExceptionally(new CatoExitException(event.getExitCode(), ready));
            };

            session.onExit.register(onExit);
            session.setServer(server);
            session.addRelatedThread(server);

            TimerTask peerConnectionTimeoutTask = Lang.setTimeout(() -> {
                future.completeExceptionally(new PeerConnectionTimeoutException());
                session.stop();
            }, 15 * 1000);

            session.onPeerConnected.register(event -> {
                session.onExit.unregister(onExit);
                future.complete(session);
                peerConnectionTimeoutTask.cancel();
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

    private static String getCatoFileName() {
        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                    return "cato-client-windows-amd64.exe";
                } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    return "cato-client-windows-arm64.exe";
                } else if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                    return "cato-client-windows-i386.exe";
                } else {
                    return "";
                }
            case OSX:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                    return "cato-client-darwin-amd64";
                } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    return "cato-client-darwin-arm64";
                } else {
                    return "";
                }
            case LINUX:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                    return "cato-client-linux-amd64";
                } else if (Architecture.SYSTEM_ARCH == Architecture.ARM32) {
                    return "cato-client-linux-arm7";
                } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    return "cato-client-linux-arm64";
                } else {
                    return "";
                }
            default:
                return "";
        }
    }

    public static String getCatoPath() {
        String name = getCatoFileName();
        if (StringUtils.isBlank(name)) return "";
        return "cato/cato/" + MultiplayerManager.CATO_VERSION + "/" + name;
    }

    public static class CatoSession extends ManagedProcess {
        private final EventManager<CatoExitEvent> onExit = new EventManager<>();
        private final EventManager<CatoIdEvent> onIdGenerated = new EventManager<>();
        private final EventManager<Event> onPeerConnected = new EventManager<>();

        private String name;
        private final State type;
        private String id;
        private boolean peerConnected = false;
        private MultiplayerClient client;
        private MultiplayerServer server;
        private final BufferedWriter writer;

        CatoSession(State type, Process process, List<String> commands) {
            super(process, commands);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            LOG.info("Started cato with command: " + new CommandBuilder().addAll(commands));

            this.type = type;
            addRelatedThread(Lang.thread(this::waitFor, "CatoExitWaiter", true));
            addRelatedThread(Lang.thread(new StreamPump(process.getInputStream(), this::checkCatoLog), "CatoInputStreamPump", true));
            addRelatedThread(Lang.thread(new StreamPump(process.getErrorStream(), this::checkCatoLog), "CatoErrorStreamPump", true));

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        public MultiplayerClient getClient() {
            return client;
        }

        public CatoSession setClient(MultiplayerClient client) {
            this.client = client;
            return this;
        }

        public MultiplayerServer getServer() {
            return server;
        }

        public CatoSession setServer(MultiplayerServer server) {
            this.server = server;
            return this;
        }

        private void checkCatoLog(String log) {
            LOG.info("Cato: " + log);
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
                LOG.info("cato exited with exitcode " + exitCode);
                onExit.fireEvent(new CatoExitEvent(this, exitCode));
            } catch (InterruptedException e) {
                onExit.fireEvent(new CatoExitEvent(this, CatoExitEvent.EXIT_CODE_INTERRUPTED));
            } finally {
                try {
                    if (writer != null)
                        writer.close();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to close cato stdin writer", e);
                }
            }
            destroyRelatedThreads();
        }

        public boolean isReady() {
            return id != null;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
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
            LOG.info("Invoking cato: " + command);
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

        public EventManager<CatoExitEvent> onExit() {
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

    public static class CatoExitEvent extends Event {
        private final int exitCode;

        public CatoExitEvent(Object source, int exitCode) {
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

    public static class IncompatibleCatoVersionException extends Exception {
        private final String expected;
        private final String actual;

        public IncompatibleCatoVersionException(String expected, String actual) {
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

    public static class CatoExitException extends RuntimeException {
        private final int exitCode;
        private final boolean ready;

        public CatoExitException(int exitCode, boolean ready) {
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

    public static class CatoExitTimeoutException extends RuntimeException {
    }

    public static class CatoSessionExpiredException extends RuntimeException {
    }

    public static class CatoAlreadyStartedException extends RuntimeException {
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

    public static class CatoNotExistsException extends RuntimeException {
        private final Path file;

        public CatoNotExistsException(Path file) {
            this.file = file;
        }

        public Path getFile() {
            return file;
        }
    }
}
