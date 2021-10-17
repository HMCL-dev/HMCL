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

import org.jackhuang.hmcl.util.Logging;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class MultiplayerClientServerTest {

    @Test
    @Ignore
    public void startServer() throws Exception {
        Logging.initForTest();
        int localPort = MultiplayerManager.findAvailablePort();
        MultiplayerServer server = new MultiplayerServer(1000, true);
        server.startServer(localPort);

        MultiplayerClient client = new MultiplayerClient("username", localPort);
        client.start();

        AtomicBoolean handshakeReceived = new AtomicBoolean(false);

        server.onHandshake().register(event -> {
            handshakeReceived.set(true);
        });

        server.onKeepAlive().register(event -> {
            client.interrupt();
            server.interrupt();
        });

        server.join();

        Assert.assertTrue(handshakeReceived.get());
    }
}
