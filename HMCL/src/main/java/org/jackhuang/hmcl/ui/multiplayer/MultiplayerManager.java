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
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cato Management.
 */
public final class MultiplayerManager {
    private static final String CATO_DOWNLOAD_URL = "https://hmcl.huangyuhui.net/maven/";
    private static final String CATO_VERSION = "2021-09-01";
    private static final Artifact CATO_ARTIFACT = new Artifact("cato", "cato", CATO_VERSION);

    private MultiplayerManager() {
    }

    public static Task<Void> downloadCato() {
        return new FileDownloadTask(
                NetworkUtils.toURL(CATO_DOWNLOAD_URL + CATO_ARTIFACT.getPath()),
                getCatoExecutable().toFile()
        );
    }

    public static Path getCatoExecutable() {
        return CATO_ARTIFACT.getPath(Metadata.HMCL_DIRECTORY);
    }

    public static CatoSession joinSession(String sessionName, String peer, int remotePort, int localPort) throws IOException {
        Path exe = getCatoExecutable();
        if (!Files.isRegularFile(exe)) {
            throw new IllegalStateException("Cato file not found");
        }
        String[] commands = new String[]{exe.toString(), "--token", "new", "--id", peer, "--from", String.format("127.0.0.1:%d", remotePort), "--to", String.format("127.0.0.1:%d", localPort)};
        Process process = new ProcessBuilder()
                .command(commands)
                .inheritIO()
                .start();
        CatoSession catoSession = new CatoSession(sessionName, process, Arrays.asList(commands));

        return catoSession;
    }

    public static CatoSession createSession(String sessionName) throws IOException {
        Path exe = getCatoExecutable();
        if (!Files.isRegularFile(exe)) {
            throw new IllegalStateException("Cato file not found");
        }
        String[] commands = new String[]{exe.toString(), "--token", "new"};
        Process process = new ProcessBuilder()
                .command(commands)
                .inheritIO()
                .start();
        CatoSession catoSession = new CatoSession(sessionName, process, Arrays.asList(commands));

        return catoSession;
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

        private final String name;
        private String id;

        CatoSession(String name, Process process, List<String> commands) {
            super(process, commands);

            this.name = name;
            addRelatedThread(Lang.thread(this::waitFor, "CatoExitWaiter", true));
            addRelatedThread(Lang.thread(new StreamPump(process.getInputStream(), it -> {
                if (id == null) {
                    Matcher matcher = TEMP_TOKEN_PATTERN.matcher(it);
                    if (matcher.find()) {
                        id = matcher.group("id");
                        onIdGenerated.fireEvent(new CatoIdEvent(this, id));
                    }
                }
            }), "CatoStreamPump", true));
        }

        private void waitFor() {
            try {
                int exitCode = getProcess().waitFor();
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

        @Nullable
        public String getId() {
            return id;
        }

        public String generateInvitationCode(int port) {
            if (id == null) {
                throw new IllegalStateException("id not generated");
            }
            String json = JsonUtils.GSON.toJson(new Invitation(id, name, port));
            return new String(Base64.getEncoder().encode(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        }

        public EventManager<CatoExitEvent> onExit() {
            return onExit;
        }

        private static final Pattern TEMP_TOKEN_PATTERN = Pattern.compile("id\\(mix(?<id>\\w+)\\)");
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
        MASTER,
        SLAVE
    }

    public static class Invitation {
        private final String id;
        private final String sessionName;
        private final int port;

        public Invitation(String id, String sessionName, int port) {
            this.id = id;
            this.sessionName = sessionName;
            this.port = port;
        }

        public String getId() {
            return id;
        }

        public String getSessionName() {
            return sessionName;
        }

        public int getPort() {
            return port;
        }
    }
}
