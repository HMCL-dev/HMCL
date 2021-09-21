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
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.launch.StreamPump;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 * Cato Management.
 */
public final class MultiplayerManager {
    private static final String CATO_DOWNLOAD_URL = "https://files.huangyuhui.net/maven/";
    private static final String CATO_VERSION = "2021-09-21";
    private static final Artifact CATO_ARTIFACT = new Artifact("cato", "cato", CATO_VERSION,
            OperatingSystem.CURRENT_OS.getCheckedName() + "-" + Architecture.CURRENT.name().toLowerCase(Locale.ROOT),
            OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "exe" : null);

    private MultiplayerManager() {
    }

    public static Task<Void> downloadCato() {
        return new FileDownloadTask(
                NetworkUtils.toURL(CATO_DOWNLOAD_URL + CATO_ARTIFACT.getPath()),
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
        return CATO_ARTIFACT.getPath(Metadata.HMCL_DIRECTORY.resolve("libraries"));
    }

    public static CatoSession joinSession(String version, String sessionName, String peer, int remotePort, int localPort) throws IOException, IncompatibleCatoVersionException {
        if (!CATO_VERSION.equals(version)) {
            throw new IncompatibleCatoVersionException(version, CATO_VERSION);
        }

        Path exe = getCatoExecutable();
        if (!Files.isRegularFile(exe)) {
            throw new IllegalStateException("Cato file not found");
        }
        String[] commands = new String[]{exe.toString(), "--token", "new", "--peer", peer, "--from", String.format("127.0.0.1:%d", localPort), "--to", String.format("127.0.0.1:%d", remotePort)};
        Process process = new ProcessBuilder()
                .command(commands)
                .start();

        CatoSession session = new CatoSession(sessionName, State.SLAVE, process, Arrays.asList(commands));
        session.addRelatedThread(Lang.thread(new LocalServerBroadcaster(localPort, session), "LocalServerBroadcaster", true));
        return session;
    }

    public static CatoSession createSession(String sessionName, int port) throws IOException {
        Path exe = getCatoExecutable();
        if (!Files.isRegularFile(exe)) {
            throw new IllegalStateException("Cato file not found");
        }
//
//        MultiplayerServer server = new MultiplayerServer(port);
//        server.start();

        String[] commands = new String[]{exe.toString(), "--token", "new", "--allows", String.format("127.0.0.1:%d", port)};
        Process process = new ProcessBuilder()
                .command(commands)
                .start();

        return new CatoSession(sessionName, State.MASTER, process, Arrays.asList(commands));
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

    public static class CatoSession extends ManagedProcess {
        private final EventManager<CatoExitEvent> onExit = new EventManager<>();
        private final EventManager<CatoIdEvent> onIdGenerated = new EventManager<>();
        private final EventManager<Event> onPeerConnected = new EventManager<>();

        private final String name;
        private final State type;
        private String id;

        CatoSession(String name, State type, Process process, List<String> commands) {
            super(process, commands);

            LOG.info("Started cato with command: " + new CommandBuilder().addAll(commands).toString());

            this.name = name;
            this.type = type;
            addRelatedThread(Lang.thread(this::waitFor, "CatoExitWaiter", true));
            addRelatedThread(Lang.thread(new StreamPump(process.getInputStream(), this::checkCatoLog), "CatoInputStreamPump", true));
            addRelatedThread(Lang.thread(new StreamPump(process.getErrorStream(), this::checkCatoLog), "CatoErrorStreamPump", true));
        }

        private void checkCatoLog(String log) {
            LOG.info("Cato: " + log);
            if (id == null) {
                Matcher matcher = TEMP_TOKEN_PATTERN.matcher(log);
                if (matcher.find()) {
                    id = "mix" + matcher.group("id");
                    onIdGenerated.fireEvent(new CatoIdEvent(this, id));
                }
            }

            {
                Matcher matcher = PEER_CONNECTED_PATTERN.matcher(log);
                if (matcher.find()) {
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

        public String generateInvitationCode(int gamePort, int serverPort) {
            if (id == null) {
                throw new IllegalStateException("id not generated");
            }
            String json = JsonUtils.GSON.toJson(new Invitation(CATO_VERSION, id, name, gamePort, serverPort));
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

        private static final Pattern TEMP_TOKEN_PATTERN = Pattern.compile("id\\(mix(?<id>\\w+)\\)");
        private static final Pattern PEER_CONNECTED_PATTERN = Pattern.compile("Peer connected");
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
        private final String version;
        private final String id;
        private final String sessionName;
        private final int gamePort;
        private final int channelPort;

        public Invitation(String version, String id, String sessionName, int gamePort, int channelPort) {
            this.version = version;
            this.id = id;
            this.sessionName = sessionName;
            this.gamePort = gamePort;
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

        public int getGamePort() {
            return gamePort;
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
}
