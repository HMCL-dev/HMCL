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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class LocalServerBroadcaster implements Runnable {
    private final int port;
    private final MultiplayerManager.CatoSession session;

    public LocalServerBroadcaster(int port, MultiplayerManager.CatoSession session) {
        this.port = port;
        this.session = session;
    }

    public int getPort() {
        return port;
    }

    @Override
    public void run() {
        DatagramSocket socket;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            LOG.log(Level.WARNING, "Failed to create datagram socket", e);
            return;
        }

        while (session.isRunning()) {
            try {
                byte[] data = String.format("[MOTD]%s[/MOTD][AD]%d[/AD]", i18n("multiplayer.session.name.motd", session.getName()), port).getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, 0, data.length, InetAddress.getByName("224.0.2.60"), 4445);
                socket.send(packet);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to send motd packet", e);
            }

            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                return;
            }
        }
    }
}
