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

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;
import org.junit.Ignore;
import org.junit.Test;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

public class LocalServerDetectorTest {

    @Test
    @Ignore("for manually testing")
    public void test() {
        try {
            for (NetworkInterface networkInterface : Lang.toIterable(NetworkInterface.getNetworkInterfaces())) {
                System.out.println(networkInterface.getName());
                for (InetAddress address : Lang.toIterable(networkInterface.getInetAddresses())) {
                    System.out.println(address);
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        Logging.initForTest();
        LocalServerDetector detector = new LocalServerDetector(3);
        detector.run();
    }
}
