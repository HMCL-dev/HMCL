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
import org.jackhuang.hmcl.util.FutureCallback;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static org.jackhuang.hmcl.ui.multiplayer.MultiplayerChannel.*;
import static org.jackhuang.hmcl.util.Logging.LOG;

public class MultiplayerServer extends Thread {
    private ServerSocket socket;
    private final String sessionName;
    private final int gamePort;
    private final boolean allowAllJoinRequests;

    private FutureCallback<CatoClient> onClientAdding;
    private final EventManager<MultiplayerChannel.CatoClient> onClientAdded = new EventManager<>();
    private final EventManager<MultiplayerChannel.CatoClient> onClientDisconnected = new EventManager<>();
    private final EventManager<Event> onKeepAlive = new EventManager<>();
    private final EventManager<Event> onHandshake = new EventManager<>();

    private final Map<String, Endpoint> clients = new ConcurrentHashMap<>();
    private final Map<String, Endpoint> nameClientMap = new ConcurrentHashMap<>();

    public MultiplayerServer(String sessionName, int gamePort, boolean allowAllJoinRequests) {
        this.sessionName = sessionName;
        this.gamePort = gamePort;
        this.allowAllJoinRequests = allowAllJoinRequests;

        setName("MultiplayerServer");
        setDaemon(true);
    }

    public void setOnClientAdding(FutureCallback<CatoClient> callback) {
        onClientAdding = callback;
    }

    public EventManager<MultiplayerChannel.CatoClient> onClientAdded() {
        return onClientAdded;
    }

    public EventManager<MultiplayerChannel.CatoClient> onClientDisconnected() {
        return onClientDisconnected;
    }

    public EventManager<Event> onKeepAlive() {
        return onKeepAlive;
    }

    public EventManager<Event> onHandshake() {
        return onHandshake;
    }

    public void startServer() throws IOException {
        startServer(0);
    }

    public void startServer(int port) throws IOException {
        if (socket != null) {
            throw new IllegalStateException("MultiplayerServer already started");
        }
        socket = new ServerSocket(port);

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

    public void kickPlayer(CatoClient player) {
        Endpoint client = nameClientMap.get(player.getUsername());
        if (client == null) return;

        try {
            if (client.socket.isConnected()) {
                client.write(new KickResponse(KickResponse.KICKED));
                client.socket.close();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to kick player " + player.getUsername() + ". Maybe already disconnected?", e);
        }
    }

    private void handleClient(Socket targetSocket) {
        String address = targetSocket.getRemoteSocketAddress().toString();
        String clientName = null;
        LOG.info("Accepted client " + address);
        try (Socket clientSocket = targetSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8))) {
            clientSocket.setKeepAlive(true);
            Endpoint endpoint = new Endpoint(clientSocket, writer);
            clients.put(address, endpoint);

            String line;
            while ((line = reader.readLine()) != null) {
                if (isInterrupted()) {
                    return;
                }

                LOG.fine("Message from client " + targetSocket.getRemoteSocketAddress() + ":" + line);
                MultiplayerChannel.Request request = JsonUtils.fromNonNullJson(line, MultiplayerChannel.Request.class);

                if (request instanceof JoinRequest) {
                    JoinRequest joinRequest = (JoinRequest) request;
                    LOG.info("Received join request with clientVersion=" + joinRequest.getClientVersion() + ", id=" + joinRequest.getUsername());
                    clientName = joinRequest.getUsername();

                    if (!Objects.equals(MultiplayerManager.CATO_VERSION, joinRequest.getClientVersion())) {
                        try {
                            endpoint.write(new KickResponse(KickResponse.VERSION_NOT_MATCHED));
                            LOG.info("Rejected join request from id=" + joinRequest.getUsername());
                            socket.close();
                        } catch (IOException e) {
                            LOG.log(Level.WARNING, "Failed to send kick response.", e);
                            return;
                        }
                    }

                    CatoClient catoClient = new CatoClient(this, clientName);
                    nameClientMap.put(clientName, endpoint);
                    onClientAdded.fireEvent(catoClient);

                    if (onClientAdding != null && !allowAllJoinRequests) {
                        onClientAdding.call(catoClient, () -> {
                            try {
                                endpoint.write(new JoinResponse(sessionName, gamePort));
                            } catch (IOException e) {
                                LOG.log(Level.WARNING, "Failed to send join response.", e);
                                try {
                                    socket.close();
                                } catch (IOException ioException) {
                                    LOG.log(Level.WARNING, "Failed to close socket caused by join response sending failure.", e);
                                    this.interrupt();
                                }
                            }
                        }, msg -> {
                            try {
                                endpoint.write(new KickResponse(msg));
                                LOG.info("Rejected join request from id=" + joinRequest.getUsername());
                                socket.close();
                            } catch (IOException e) {
                                LOG.log(Level.WARNING, "Failed to send kick response.", e);
                            }
                        });
                    } else {
                        // Allow all join requests.
                        endpoint.write(new JoinResponse(sessionName, gamePort));
                    }
                } else if (request instanceof KeepAliveRequest) {
                    endpoint.write(new KeepAliveResponse(System.currentTimeMillis()));

                    onKeepAlive.fireEvent(new Event(this));
                } else if (request instanceof HandshakeRequest) {
                    endpoint.write(new HandshakeResponse());

                    onHandshake.fireEvent(new Event(this));
                } else {
                    LOG.log(Level.WARNING, "Unrecognized packet from client " + targetSocket.getRemoteSocketAddress() + ":" + line);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to handle client socket.", e);
        } catch (JsonParseException e) {
            LOG.log(Level.SEVERE, "Failed to parse client request. This should not happen.", e);
        } finally {
            if (clientName != null) {
                onClientDisconnected.fireEvent(new CatoClient(this, clientName));
            }

            clients.remove(address);
            if (clientName != null) nameClientMap.remove(clientName);
        }
    }

    public static class Endpoint {
        public final Socket socket;
        public final BufferedWriter writer;

        public Endpoint(Socket socket, BufferedWriter writer) {
            this.socket = socket;
            this.writer = writer;
        }

        public synchronized void write(Object object) throws IOException {
            writer.write(verifyJson(JsonUtils.UGLY_GSON.toJson(object)));
            writer.newLine();
            writer.flush();
        }
    }
}
