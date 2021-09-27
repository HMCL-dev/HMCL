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
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;

import static org.jackhuang.hmcl.ui.multiplayer.MultiplayerChannel.*;
import static org.jackhuang.hmcl.util.Logging.LOG;

public class MultiplayerServer extends Thread {
    private ServerSocket socket;
    private final int gamePort;

    private final EventManager<MultiplayerChannel.CatoClient> onClientAdded = new EventManager<>();
    private final EventManager<MultiplayerChannel.CatoClient> onClientDisconnected = new EventManager<>();
    private final EventManager<Event> onKeepAlive = new EventManager<>();

    public MultiplayerServer(int gamePort) {
        this.gamePort = gamePort;

        setName("MultiplayerServer");
        setDaemon(true);
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

    private void handleClient(Socket targetSocket) {
        String clientName = null;
        LOG.info("Accepted client " + targetSocket.getRemoteSocketAddress());
        try (Socket clientSocket = targetSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {
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

                    writer.write(verifyJson(JsonUtils.UGLY_GSON.toJson(new JoinResponse(gamePort))));
                    writer.newLine();
                    writer.flush();

                    clientName = joinRequest.getUsername();
                    onClientAdded.fireEvent(new CatoClient(this, joinRequest.getUsername()));
                } else if (request instanceof KeepAliveRequest) {
                    writer.write(JsonUtils.UGLY_GSON.toJson(new KeepAliveResponse(System.currentTimeMillis())));
                    writer.newLine();
                    writer.flush();

                    onKeepAlive.fireEvent(new Event(this));
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
        }
    }
}
