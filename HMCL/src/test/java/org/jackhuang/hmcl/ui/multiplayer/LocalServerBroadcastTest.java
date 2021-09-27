/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class LocalServerBroadcastTest {

    @Test
    @Ignore("for manually testing")
    public void test() {
        int port = 12345;
        DatagramSocket socket;
        InetAddress broadcastAddress;
        try {
            socket = new DatagramSocket();
            broadcastAddress = InetAddress.getByName("224.0.2.60");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        while (true) {
            try {
                byte[] data = String.format("[MOTD]%s[/MOTD][AD]%d[/AD]", i18n("multiplayer.session.name.motd", "Test server"), port).getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, 0, data.length, broadcastAddress, 4445);
                socket.send(packet);
                System.out.println("Broadcast server 127.0.0.1:" + port);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                return;
            }
        }
    }

    @Test
    @Ignore
    public void printLocalAddress() throws IOException {
        DatagramSocket socket = new DatagramSocket(new InetSocketAddress((InetAddress) null, 4444));
        System.out.println(socket.getLocalAddress());
    }
}
