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
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 * Cato Management.
 */
public final class MultiplayerManager {
    private static final String CATO_DOWNLOAD_URL = "https://files.huangyuhui.net/maven/";
    static final String CATO_VERSION = "1.0.c";
    private static final String CATO_PATH = getCatoPath();
    public static final int CATO_AGREEMENT_VERSION = 2;

    private static final String REMOTE_ADDRESS = "127.0.0.1";
    private static final String LOCAL_ADDRESS = "0.0.0.0";

    private MultiplayerManager() {
    }

    public static Task<Void> downloadCato() {
        return new FileDownloadTask(
                NetworkUtils.toURL(CATO_DOWNLOAD_URL + CATO_PATH),
                getCatoExecutable().toFile()
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

    public static CompletableFuture<CatoSession> joinSession(String token, String version, String sessionName, String peer, Mode mode, int remotePort, int localPort) throws IncompatibleCatoVersionException {
        if (!CATO_VERSION.equals(version)) {
            throw new IncompatibleCatoVersionException(version, CATO_VERSION);
        }

        Path exe = getCatoExecutable();
        if (!Files.isRegularFile(exe)) {
            throw new IllegalStateException("Cato file not found");
        }

        return CompletableFuture.completedFuture(null).thenComposeAsync(unused -> {
            if (!isPortAvailable(3478)) {
                throw new CatoAlreadyStartedException();
            }

            String[] commands = new String[]{exe.toString(),
                    "--token", StringUtils.isBlank(token) ? "new" : token,
                    "--id", peer,
                    "--local", String.format("%s:%d", LOCAL_ADDRESS, localPort),
                    "--remote", String.format("%s:%d", REMOTE_ADDRESS, remotePort),
                    "--mode", mode.getName()};
            Process process;
            try {
                process = new ProcessBuilder()
                        .command(commands)
                        .start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            CatoSession session = new CatoSession(sessionName, State.SLAVE, process, Arrays.asList(commands));

            CompletableFuture<CatoSession> future = new CompletableFuture<>();

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            session.onExit().register(() -> {
                try {
                    writer.close();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to close cato session stdin writer", e);
                }
            });

            session.onPeerConnected.register(event -> {
                MultiplayerClient client = new MultiplayerClient(session.getId(), localPort);
                session.addRelatedThread(client);
                session.setClient(client);
                client.onConnected().register(connectedEvent -> {
                    try {
                        int port = findAvailablePort();
                        String command = String.format("net add %s %s:%d %s:%d %s", peer, LOCAL_ADDRESS, port, REMOTE_ADDRESS, connectedEvent.getPort(), mode.getName());
                        LOG.info("Invoking cato: " + command);
                        session.addRelatedThread(Lang.thread(new LocalServerBroadcaster(port, session), "LocalServerBroadcaster", true));
                        client.setGamePort(port);
                        writer.write(command);
                        writer.newLine();
                        writer.flush();
                        future.complete(session);
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                        session.stop();
                    }
                });
                client.onKicked().register(kickedEvent -> {
                    future.completeExceptionally(new CancellationException());
                    session.stop();
                });
                client.start();
            });

            return future;
        });
    }

    public static CatoSession createSession(String token, String sessionName, int gamePort) throws IOException {
        Path exe = getCatoExecutable();
        if (!Files.isRegularFile(exe)) {
            throw new IllegalStateException("Cato file not found");
        }

        if (!isPortAvailable(3478)) {
            throw new CatoAlreadyStartedException();
        }

        MultiplayerServer server = new MultiplayerServer(gamePort);
        server.startServer();

        String[] commands = new String[]{exe.toString(),
                "--token", StringUtils.isBlank(token) ? "new" : token,
                "--allows", String.format("%s:%d/%s:%d", REMOTE_ADDRESS, server.getPort(), REMOTE_ADDRESS, gamePort)};
        Process process = new ProcessBuilder()
                .command(commands)
                .start();

        CatoSession session = new CatoSession(sessionName, State.MASTER, process, Arrays.asList(commands));
        session.setServer(server);
        session.addRelatedThread(server);
        return session;
    }

    public static Invitation parseInvitationCode(String invitationCode) throws JsonParseException {
        String json = new String(Base64.getDecoder().decode(invitationCode.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        return JsonUtils.fromNonNullJson(json, Invitation.class);
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

    public static String getCatoPath() {
        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                if (Architecture.CURRENT == Architecture.X86_64) {
                    return "cato/cato/" + MultiplayerManager.CATO_VERSION + "/cato-windows-amd64.exe";
                } else {
                    return "";
                }
            case OSX:
                if (Architecture.CURRENT == Architecture.X86_64) {
                    return "cato/cato/" + MultiplayerManager.CATO_VERSION + "/cato-darwin-amd64";
                } else if (Architecture.CURRENT == Architecture.ARM64) {
                    return "cato/cato/" + MultiplayerManager.CATO_VERSION + "/cato-darwin-arm64";
                } else {
                    return "";
                }
            case LINUX:
                if (Architecture.CURRENT == Architecture.X86_64) {
                    return "cato/cato/" + MultiplayerManager.CATO_VERSION + "/cato-linux-amd64";
                } else if (Architecture.CURRENT == Architecture.ARM || Architecture.CURRENT == Architecture.ARM64) {
                    return "cato/cato/" + MultiplayerManager.CATO_VERSION + "/cato-linux-arm7";
                } else {
                    return "";
                }
            default:
                return "";
        }
    }

    public static class CatoSession extends ManagedProcess {
        private final EventManager<CatoExitEvent> onExit = new EventManager<>();
        private final EventManager<CatoIdEvent> onIdGenerated = new EventManager<>();
        private final EventManager<Event> onPeerConnected = new EventManager<>();

        private final String name;
        private final State type;
        private String id;
        private boolean peerConnected = false;
        private MultiplayerClient client;
        private MultiplayerServer server;

        CatoSession(String name, State type, Process process, List<String> commands) {
            super(process, commands);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            LOG.info("Started cato with command: " + new CommandBuilder().addAll(commands).toString());

            this.name = name;
            this.type = type;
            addRelatedThread(Lang.thread(this::waitFor, "CatoExitWaiter", true));
            addRelatedThread(Lang.thread(new StreamPump(process.getInputStream(), this::checkCatoLog), "CatoInputStreamPump", true));
            addRelatedThread(Lang.thread(new StreamPump(process.getErrorStream(), this::checkCatoLog), "CatoErrorStreamPump", true));
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
            }
            destroyRelatedThreads();
        }

        public boolean isReady() {
            return id != null;
        }

        public String getName() {
            return name;
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
            String json = JsonUtils.GSON.toJson(new Invitation(CATO_VERSION, id, name, serverPort));
            return new String(Base64.getEncoder().encode(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
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
        @SerializedName("v")
        private final String version;
        private final String id;
        @SerializedName("n")
        private final String sessionName;
        @SerializedName("p")
        private final int channelPort;

        public Invitation(String version, String id, String sessionName, int channelPort) {
            this.version = version;
            this.id = id;
            this.sessionName = sessionName;
            this.channelPort = channelPort;
        }

        public String getVersion() {
            return version;
        }

        public String getId() {
            return id;
        }

        public String getSessionName() {
            return sessionName;
        }

        public int getChannelPort() {
            return channelPort;
        }
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
        RELAY;

        String getName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static class CatoAlreadyStartedException extends RuntimeException {
    }
}
