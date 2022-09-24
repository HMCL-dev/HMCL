/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class LocalServerBroadcaster implements AutoCloseable {
    private final String address;
    private final ThreadGroup threadGroup = new ThreadGroup("JoinSession");

    private final EventManager<Event> onExit = new EventManager<>();

    private boolean running = true;

    public LocalServerBroadcaster(String address) {
        this.address = address;
        this.threadGroup.setDaemon(true);
    }

    @Override
    public void close() {
        running = false;
        threadGroup.interrupt();
    }

    public String getAddress() {
        return address;
    }

    public EventManager<Event> onExit() {
        return onExit;
    }

    public static final Pattern ADDRESS_PATTERN = Pattern.compile("^\\s*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d{1,5})\\s*$");

    public void start() {
        Thread forwardPortThread = new Thread(threadGroup, this::forwardPort, "ForwardPort");
        forwardPortThread.start();
    }

    private void forwardPort() {
        try {
            Matcher matcher = ADDRESS_PATTERN.matcher(address);
            if (!matcher.find()) {
                throw new MalformedURLException();
            }
            try (Socket forwardingSocket = new Socket();
                 ServerSocket serverSocket = new ServerSocket()) {
                forwardingSocket.setSoTimeout(30000);
                forwardingSocket.connect(new InetSocketAddress(matcher.group(1), Lang.parseInt(matcher.group(2), 0)));

                serverSocket.bind(null);

                Thread broadcastMOTDThread = new Thread(threadGroup, () -> broadcastMOTD(serverSocket.getLocalPort()), "BroadcastMOTD");
                broadcastMOTDThread.start();

                LOG.log(Level.INFO, "Listening " + serverSocket.getLocalSocketAddress());

                while (running) {
                    Socket forwardedSocket = serverSocket.accept();
                    LOG.log(Level.INFO, "Accepting client");
                    new Thread(threadGroup, () -> forwardTraffic(forwardingSocket, forwardedSocket), "Forward S->D").start();
                    new Thread(threadGroup, () -> forwardTraffic(forwardedSocket, forwardingSocket), "Forward D->S").start();
                }
            }
        } catch (IOException | UnresolvedAddressException e) {
            LOG.log(Level.WARNING, "Error in forwarding port", e);
        } finally {
            close();
            onExit.fireEvent(new Event(this));
        }
    }

    private void forwardTraffic(Socket src, Socket dest) {
        try (InputStream is = src.getInputStream(); OutputStream os = dest.getOutputStream()) {
            byte[] buf = new byte[1024];
            while (true) {
                int len = is.read(buf, 0, buf.length);
                if (len < 0) break;
                LOG.log(Level.INFO, "Forwarding buffer " + len);
                os.write(buf, 0, len);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Disconnected", e);
        }
    }

    private void broadcastMOTD(int port) {
        DatagramSocket socket;
        InetAddress broadcastAddress;
        try {
            socket = new DatagramSocket();
            broadcastAddress = InetAddress.getByName("224.0.2.60");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to create datagram socket", e);
            return;
        }

        while (running) {
            try {
                byte[] data = String.format("[MOTD]%s[/MOTD][AD]%d[/AD]", i18n("multiplayer.session.name.motd"), port).getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, 0, data.length, broadcastAddress, 4445);
                socket.send(packet);
                LOG.finest("Broadcast server 0.0.0.0:" + port);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to send motd packet", e);
            }

            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                return;
            }
        }

        socket.close();
    }
}
