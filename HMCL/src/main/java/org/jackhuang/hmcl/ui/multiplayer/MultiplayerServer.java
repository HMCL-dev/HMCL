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
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonSubtype;
import org.jackhuang.hmcl.util.gson.JsonType;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class MultiplayerServer {
    private ServerSocket socket;
    private Thread thread;
    private final int gamePort;

    public MultiplayerServer(int gamePort) {
        this.gamePort = gamePort;
    }

    public void start() throws IOException {
        if (socket != null) {
            throw new IllegalStateException("MultiplayerServer already started");
        }
        socket = new ServerSocket(0);

        Lang.thread(this::run, "MultiplayerServer", true);
    }

    public int getPort() {
        if (socket == null) {
            throw new IllegalStateException("MultiplayerServer not started");
        }

        return socket.getLocalPort();
    }

    private void run() {
        try {
            while (true) {
                Socket clientSocket = socket.accept();
                Lang.thread(() -> handleClient(clientSocket), "MultiplayerServerClientThread", true);
            }
        } catch (IOException e) {

        }
    }

    private void handleClient(Socket targetSocket) {
        try (Socket clientSocket = targetSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Request request = JsonUtils.fromNonNullJson(line, Request.class);
                request.process(this, writer);
            }
        } catch (IOException | JsonParseException e) {

        }
    }

    @JsonType(
            property = "type",
            subtypes = {
                    @JsonSubtype(clazz = JoinRequest.class, name = "join")
            }
    )
    public static class Request {

        public void process(MultiplayerServer server, BufferedWriter writer) throws IOException, JsonParseException {
        }
    }

    public static class JoinRequest extends Request {
        private final String clientLauncherVersion;
        private final String username;

        public JoinRequest(String clientLauncherVersion, String username) {
            this.clientLauncherVersion = clientLauncherVersion;
            this.username = username;
        }

        public String getClientLauncherVersion() {
            return clientLauncherVersion;
        }

        public String getUsername() {
            return username;
        }

        @Override
        public void process(MultiplayerServer server, BufferedWriter writer) throws IOException, JsonParseException {
            writer.write(JsonUtils.GSON.toJson(new JoinResponse(server.gamePort)));
        }
    }

    @JsonType(
            property = "type",
            subtypes = {
                    @JsonSubtype(clazz = JoinResponse.class, name = "join")
            }
    )
    public static class Response {

    }

    public static class JoinResponse extends Response {
        private final int port;

        public JoinResponse(int port) {
            this.port = port;
        }

        public int getPort() {
            return port;
        }
    }
}
