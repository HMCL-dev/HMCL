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
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;

import static org.jackhuang.hmcl.ui.multiplayer.MultiplayerChannel.*;
import static org.jackhuang.hmcl.util.Logging.LOG;

public class MultiplayerClient extends Thread {
    private final String id;
    private final int port;

    private int gamePort;

    private final EventManager<ConnectedEvent> onConnected = new EventManager<>();
    private final EventManager<Event> onDisconnected = new EventManager<>();

    public MultiplayerClient(String id, int port) {
        this.id = id;
        this.port = port;

        setName("MultiplayerClient");
        setDaemon(true);
    }

    public void setGamePort(int gamePort) {
        this.gamePort = gamePort;
    }

    public int getGamePort() {
        return gamePort;
    }

    public EventManager<ConnectedEvent> onConnected() {
        return onConnected;
    }

    public EventManager<Event> onDisconnected() {
        return onDisconnected;
    }

    @Override
    public void run() {
        LOG.info("Connecting to 127.0.0.1:" + port);
        for (int i = 0; i < 5; i++) {
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                LOG.info("Connected to 127.0.0.1:" + port);

                writer.write(JsonUtils.UGLY_GSON.toJson(new JoinRequest(MultiplayerManager.CATO_VERSION, id)));
                writer.newLine();
                writer.flush();

                LOG.fine("Sent join request with id=" + id);

                String line = reader.readLine();
                if (line == null) {
                    return;
                }

                JoinResponse response = JsonUtils.fromNonNullJson(line, JoinResponse.class);
                setGamePort(response.getPort());
                onConnected.fireEvent(new ConnectedEvent(this, response.getPort()));

                LOG.fine("Received join response with port " + response.getPort());

                while (!isInterrupted()) {
                    writer.write(verifyJson(JsonUtils.UGLY_GSON.toJson(new KeepAliveResponse(System.currentTimeMillis()))));
                    writer.newLine();
                    writer.flush();

                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        LOG.warning("MultiplayerClient interrupted");
                        return;
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
            }
        }
        LOG.info("Lost connection to 127.0.0.1:" + port);
        onDisconnected.fireEvent(new Event(this));
    }

    public static class ConnectedEvent extends Event {
        private final int port;

        public ConnectedEvent(Object source, int port) {
            super(source);
            this.port = port;
        }

        public int getPort() {
            return port;
        }
    }
}
