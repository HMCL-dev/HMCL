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
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.ManagedProcess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cato Management.
 */
public final class MultiplayerManager {
    private static final String CATO_DOWNLOAD_URL = "https://hmcl.huangyuhui.net/maven/";
    private static final String CATO_VERSION = "2021-09-01";
    private static final Artifact CATO_ARTIFACT = new Artifact("cato", "cato", CATO_VERSION);

    private MultiplayerManager() {
    }

    public static void fetchIdAndToken() {
        // TODO
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

    public static ManagedProcess joinRoom(String token, String peer, String localAddress, String remoteAddress) throws IOException {
        Path exe = getCatoExecutable();
        if (!Files.isRegularFile(exe)) {
            throw new IllegalStateException("Cato file not found");
        }
        String[] commands = new String[]{exe.toString(), "--peer", peer, "--from", localAddress, "--to", remoteAddress};
        Process process = new ProcessBuilder()
                .command(commands)
                .inheritIO()
                .start();
        ManagedProcess managedProcess = new ManagedProcess(process, Arrays.asList(commands));
        managedProcess.getProcess().getOutputStream().write(token.getBytes(StandardCharsets.UTF_8));
        return managedProcess;
    }

    public static ManagedProcess createRoom() throws IOException {
        Path exe = getCatoExecutable();
        if (!Files.isRegularFile(exe)) {
            throw new IllegalStateException("Cato file not found");
        }
        String[] commands = new String[]{exe.toString(), "--token", "new"};
        Process process = new ProcessBuilder()
                .command(commands)
                .inheritIO()
                .start();
        ManagedProcess managedProcess = new ManagedProcess(process, Arrays.asList(commands));
//
//        Thread stdout = Lang.thread(new StreamPump(managedProcess.getProcess().getInputStream()), it -> {
//
//        });
//
//        Thread exitWaiter = Lang.thread(() -> {
//            int exitCode = process.waitFor();
//            if (exitCode != 0) {
//
//            }
//        })

//        managedProcess.addRelatedThread(stdout);
//        managedProcess.addRelatedThread(exitWaiter);

        return managedProcess;
    }

    public static String generateInvitationCode(String id, int port) {
        String json = JsonUtils.GSON.toJson(new Invitation(id, port));
        return new String(Base64.getEncoder().encode(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    public static Invitation parseInvitationCode(String invitationCode) throws JsonParseException {
        String json = new String(Base64.getDecoder().decode(invitationCode.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        return JsonUtils.fromNonNullJson(json, Invitation.class);
    }

    enum State {
        DISCONNECTED,
        MASTER,
        SLAVE
    }

    public static class Invitation {
        private final String id;
        private final int port;

        public Invitation(String id, int port) {
            this.id = id;
            this.port = port;
        }

        public String getId() {
            return id;
        }

        public int getPort() {
            return port;
        }
    }
}
