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
import org.jackhuang.hmcl.util.gson.JsonSubtype;
import org.jackhuang.hmcl.util.gson.JsonType;

public final class MultiplayerChannel {

    private MultiplayerChannel() {
    }

    @JsonType(
            property = "type",
            subtypes = {
                    @JsonSubtype(clazz = JoinRequest.class, name = "join"),
                    @JsonSubtype(clazz = KeepAliveRequest.class, name = "keepalive")
            }
    )
    public static class Request {
    }

    public static class JoinRequest extends Request {
        private final String clientVersion;
        private final String username;

        public JoinRequest(String clientVersion, String username) {
            this.clientVersion = clientVersion;
            this.username = username;
        }

        public String getClientVersion() {
            return clientVersion;
        }

        public String getUsername() {
            return username;
        }
    }

    public static class KeepAliveRequest extends Request {
        private final long timestamp;

        public KeepAliveRequest(long timestamp) {
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    @JsonType(
            property = "type",
            subtypes = {
                    @JsonSubtype(clazz = JoinResponse.class, name = "join"),
                    @JsonSubtype(clazz = KeepAliveResponse.class, name = "keepalive")
            }
    )
    public static class Response {

    }

    public static class JoinResponse extends Response {
        private final int port;

        public JoinResponse(int port) {
            this.port = port;
        }

        public int getPort() {
            return port;
        }
    }

    public static class KeepAliveResponse extends Response {
        private final long timestamp;

        public KeepAliveResponse(long timestamp) {
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public static class CatoClient extends Event {
        private final String username;

        public CatoClient(Object source, String username) {
            super(source);
            this.username = username;
        }

        public String getUsername() {
            return username;
        }
    }

    public static String verifyJson(String jsonString) {
        if (jsonString.indexOf('\r') >= 0 || jsonString.indexOf('\n') >= 0) {
            throw new IllegalArgumentException();
        }
        return jsonString;
    }
}
