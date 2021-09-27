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
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonSubtype;
import org.jackhuang.hmcl.util.gson.JsonType;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class MultiplayerServer extends Thread {
    private ServerSocket socket;
    private final int gamePort;

    private final EventManager<CatoClient> onClientAdded = new EventManager<CatoClient>();

    public MultiplayerServer(int gamePort) {
        this.gamePort = gamePort;

        setName("MultiplayerServer");
        setDaemon(true);
    }

    public EventManager<CatoClient> onClientAdded() {
        return onClientAdded;
    }

    public void startServer() throws IOException {
        if (socket != null) {
            throw new IllegalStateException("MultiplayerServer already started");
        }
        socket = new ServerSocket(0);

        start();
    }

    public int getPort() {
        if (socket == null) {
            throw new IllegalStateException("MultiplayerServer not started");
        }

        return socket.getLocalPort();
    }

    @Override
    public void run() {
        LOG.info("Multiplayer Server listening 127.0.0.1:" + socket.getLocalPort());
        try {
            while (!isInterrupted()) {
                Socket clientSocket = socket.accept();
                clientSocket.setSoTimeout(10000);
                Lang.thread(() -> handleClient(clientSocket), "MultiplayerServerClientThread", true);
            }
        } catch (IOException ignored) {
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
        } catch (IOException | JsonParseException ignored) {
        }
    }

    @JsonType(
            property = "type",
            subtypes = {
                    @JsonSubtype(clazz = JoinRequest.class, name = "join"),
                    @JsonSubtype(clazz = KeepAliveRequest.class, name = "keepalive")
            }
    )
    public static class Request {

        public void process(MultiplayerServer server, BufferedWriter writer) throws IOException, JsonParseException {
        }
    }

    public static class JoinRequest extends Request {
        private final String clientVersion;
        private final String username;

        public JoinRequest(String clientVersion, String username) {
            this.clientVersion = clientVersion;
            this.username = username;
        }

        public String getClientVersion() {
            return clientVersion;
        }

        public String getUsername() {
            return username;
        }

        @Override
        public void process(MultiplayerServer server, BufferedWriter writer) throws IOException, JsonParseException {
            LOG.fine("Received join request with clientVersion=" + clientVersion + ", id=" + username);

            writer.write(JsonUtils.GSON.toJson(new JoinResponse(server.gamePort)));

            server.onClientAdded.fireEvent(new CatoClient(server, username));
        }
    }

    public static class KeepAliveRequest extends Request {
        private final long timestamp;

        public KeepAliveRequest(long timestamp) {
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public void process(MultiplayerServer server, BufferedWriter writer) throws IOException, JsonParseException {
            writer.write(JsonUtils.GSON.toJson(new KeepAliveResponse(System.currentTimeMillis())));
        }
    }

    @JsonType(
            property = "type",
            subtypes = {
                    @JsonSubtype(clazz = JoinResponse.class, name = "join"),
                    @JsonSubtype(clazz = KeepAliveResponse.class, name = "keepalive")
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

    public static class KeepAliveResponse extends Response {
        private final long timestamp;

        public KeepAliveResponse(long timestamp) {
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public static class CatoClient extends Event {
        private final String username;

        public CatoClient(Object source, String username) {
            super(source);
            this.username = username;
        }

        public String getUsername() {
            return username;
        }
    }
}
