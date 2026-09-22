/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.server;

public record ServerStatus(
        // https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping#Pong_Response
        long networkLatency,

        // https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping#Status_Response
        int protocol,
        String protocolName,

        int playerMax,
        int playerOnline,
        String favicon
) {
}
