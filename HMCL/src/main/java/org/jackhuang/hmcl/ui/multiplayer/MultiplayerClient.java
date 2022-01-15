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
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.TimerTask;
import java.util.logging.Level;

import static org.jackhuang.hmcl.ui.multiplayer.MultiplayerChannel.*;
import static org.jackhuang.hmcl.util.Logging.LOG;

public class MultiplayerClient extends Thread {
    private final String id;
    private final int port;

    private int gamePort;
    private boolean connected = false;

    private final EventManager<ConnectedEvent> onConnected = new EventManager<>();
    private final EventManager<Event> onDisconnected = new EventManager<>();
    private final EventManager<KickEvent> onKicked = new EventManager<>();
    private final EventManager<Event> onHandshake = new EventManager<>();

    public MultiplayerClient(String id, int port) {
        this.id = id;
        this.port = port;

        setName("MultiplayerClient");
        setDaemon(true);
    }

    public synchronized void setGamePort(int gamePort) {
        this.gamePort = gamePort;
    }

    public synchronized int getGamePort() {
        return gamePort;
    }

    public EventManager<ConnectedEvent> onConnected() {
        return onConnected;
    }

    public EventManager<Event> onDisconnected() {
        return onDisconnected;
    }

    public EventManager<KickEvent> onKicked() {
        return onKicked;
    }

    public EventManager<Event> onHandshake() {
        return onHandshake;
    }

    @Override
    public void run() {
        LOG.info("Connecting to 127.0.0.1:" + port);
        for (int i = 0; i < 5; i++) {
            KeepAliveThread keepAliveThread = null;
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                MultiplayerServer.Endpoint endpoint = new MultiplayerServer.Endpoint(socket, writer);
                socket.setSoTimeout(30000);
                LOG.info("Connected to 127.0.0.1:" + port);

                endpoint.write(new HandshakeRequest());
                endpoint.write(new JoinRequest(MultiplayerManager.CATO_VERSION, id));

                LOG.fine("Sent join request with id=" + id);

                keepAliveThread = new KeepAliveThread(endpoint);
                keepAliveThread.start();

                TimerTask task = Lang.setTimeout(() -> {
                    // If after 15 seconds, we didn't receive the HandshakeResponse,
                    // We fail to establish the connection with server.

                    try {
                        LOG.log(Level.WARNING, "Socket connection timeout, closing socket");
                        socket.close();
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Failed to close socket", e);
                    }
                }, 25 * 1000);

                String line;
                while ((line = reader.readLine()) != null) {
                    if (isInterrupted()) {
                        return;
                    }

                    LOG.fine("Message from server:" + line);

                    Response response = JsonUtils.fromNonNullJson(line, Response.class);

                    if (response instanceof JoinResponse) {
                        JoinResponse joinResponse = JsonUtils.fromNonNullJson(line, JoinResponse.class);
                        setGamePort(joinResponse.getPort());

                        connected = true;

                        onConnected.fireEvent(new ConnectedEvent(this, joinResponse.getSessionName(), joinResponse.getPort()));

                        LOG.fine("Received join response with port " + joinResponse.getPort());
                    } else if (response instanceof KickResponse) {
                        LOG.fine("Kicked by the server");
                        onKicked.fireEvent(new KickEvent(this, ((KickResponse) response).getMsg()));
                        return;
                    } else if (response instanceof KeepAliveResponse) {
                    } else if (response instanceof HandshakeResponse) {
                        LOG.fine("Established connection with server");
                        onHandshake.fireEvent(new Event(this));
                        task.cancel();
                    } else {
                        LOG.log(Level.WARNING, "Unrecognized packet from server:" + line);
                    }
                }
            } catch (ConnectException e) {
                LOG.info("Failed to connect to 127.0.0.1:" + port + ", tried " + i + " time(s)");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    LOG.warning("MultiplayerClient interrupted");
                    return;
                }
                continue;
            } catch (IOException | JsonParseException e) {
                e.printStackTrace();
            } finally {
                if (keepAliveThread != null) {
                    keepAliveThread.interrupt();
                }
            }
        }
        LOG.info("Lost connection to 127.0.0.1:" + port);
        onDisconnected.fireEvent(new Event(this));
    }

    public boolean isConnected() {
        return connected;
    }

    private static class KeepAliveThread extends Thread {

        private final MultiplayerServer.Endpoint endpoint;

        public KeepAliveThread(MultiplayerServer.Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    endpoint.write(new KeepAliveRequest(System.currentTimeMillis()));
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to send keep alive packet", e);
                    break;
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public static class ConnectedEvent extends Event {
        private final String sessionName;
        private final int port;

        public ConnectedEvent(Object source, String sessionName, int port) {
            super(source);
            this.sessionName = sessionName;
            this.port = port;
        }

        public String getSessionName() {
            return sessionName;
        }

        public int getPort() {
            return port;
        }
    }

    public static class KickEvent extends Event {
        private final String reason;

        public KickEvent(Object source, String reason) {
            super(source);
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}
