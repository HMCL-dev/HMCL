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

import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class LocalServerDetector extends Thread {

    private final EventManager<DetectedLanServerEvent> onDetectedLanServer = new EventManager<>();
    private final int retry;

    public LocalServerDetector(int retry) {
        this.retry = retry;

        setName("LocalServerDetector");
        setDaemon(true);
    }

    public EventManager<DetectedLanServerEvent> onDetectedLanServer() {
        return onDetectedLanServer;
    }

    @Override
    public void run() {
        MulticastSocket socket;
        InetAddress broadcastAddress;
        try {
            socket = new MulticastSocket(4445);
            socket.setSoTimeout(5000);
            socket.joinGroup(broadcastAddress = InetAddress.getByName("224.0.2.60"));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to create datagram socket", e);
            return;
        }

        byte[] buf = new byte[1024];

        int tried = 0;
        while (!isInterrupted()) {
            DatagramPacket packet = new DatagramPacket(buf, 1024);

            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                if (tried++ > retry) {
                    onDetectedLanServer.fireEvent(new DetectedLanServerEvent(this, null));
                    break;
                }

                continue;
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to detect lan server", e);
                break;
            }

            String response = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
            LOG.fine("Local server " + packet.getAddress() + ":" + packet.getPort() + " broadcast message: " + response);
            onDetectedLanServer.fireEvent(new DetectedLanServerEvent(this, PingResponse.parsePingResponse(response)));
            break;
        }

        try {
            socket.leaveGroup(broadcastAddress);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to leave multicast listening group", e);
        }

        socket.close();
    }

    public static class DetectedLanServerEvent extends Event {
        private final PingResponse lanServer;

        public DetectedLanServerEvent(Object source, PingResponse lanServer) {
            super(source);
            this.lanServer = lanServer;
        }

        public PingResponse getLanServer() {
            return lanServer;
        }
    }

    public static class PingResponse {
        private final String motd;
        private final Integer ad;

        public PingResponse(String motd, Integer ad) {
            this.motd = motd;
            this.ad = ad;
        }

        public String getMotd() {
            return motd;
        }

        public Integer getAd() {
            return ad;
        }

        public boolean isValid() {
            return ad != null;
        }

        public static PingResponse parsePingResponse(String message) {
            return new PingResponse(
                    StringUtils.substringBefore(
                            StringUtils.substringAfter(message, "[MOTD]"),
                            "[/MOTD]"),
                    Lang.toIntOrNull(StringUtils.substringBefore(
                            StringUtils.substringAfter(message, "[AD]"),
                            "[/AD]"))
            );
        }
    }
}
