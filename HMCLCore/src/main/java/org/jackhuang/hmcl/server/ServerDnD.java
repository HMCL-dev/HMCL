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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Drag-and-drop payload for a Minecraft server definition.
 * <p>
 * Format: {@code minecraft-server:Base64(JSON)}
 *
 * <p>
 * Required fields:
 * <ul>
 *   <li>{@code name}: the display name of the server.</li>
 *   <li>{@code address}: the server address, such as
 *   "...@server.ip.example.com:25565?_id=xxx&key=value...".</li>
 * </ul>
 *
 * <p>
 * Optional fields:
 * <ul>
 *   <li>{@code favicon}: the server icon. This value should be the raw PNG base64 payload only, without
 *   the {@code data:image/png;base64,} prefix. The parser in {@link ServerStatusPinger} accepts the
 *   Minecraft status format where the remote payload may include a data URL, but it strips the prefix and
 *   keeps only the base64 bytes. This drag-and-drop payload therefore stores the base64 text directly,
 *   not the full data URL.</li>
 * </ul>
 *
 * Example payload:
 * <pre>
 * {
 *   "name": "server name",
 *   "address": "...@server.ip.example.com:25565?_id=xxx&key=value...",
 *   "favicon": "iVBORw0KGgoAAAANSUhEUgAA..."
 * }
 * </pre>
 */
public final class ServerDnD {
    private static final String SCHEME = "minecraft-server";

    private ServerDnD() {
    }

    public static Optional<Server> parseUrlFromDragboard(Dragboard dragboard) {
        String url = dragboard.getString();
        if (url == null) return Optional.empty();

        int index = url.indexOf(":");
        if (index == -1) return Optional.empty();
        if (!SCHEME.equals(url.substring(0, index))) return Optional.empty();
        try {
            JsonObject object = JsonParser.parseString(new String(Base64.getDecoder().decode(url.substring(index + 1)), StandardCharsets.UTF_8)).getAsJsonObject();
            String favicon = null;
            if (object.has("favicon")) {
                favicon = object.get("favicon").getAsString();
            }
            String address = object.get("address").getAsString();
            String name = object.get("name").getAsString();
            return Optional.of(new Server(
                    Server.ServerPackStatus.PROMPT,
                    false,
                    favicon,
                    address,
                    name
            ));
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    public static EventHandler<DragEvent> dragOverHandler() {
        return event -> parseUrlFromDragboard(event.getDragboard()).ifPresent(url -> {
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
    }

    public static EventHandler<DragEvent> dragDroppedHandler(Consumer<Server> onServerTransfered) {
        return event -> parseUrlFromDragboard(event.getDragboard()).ifPresent(server -> {
            event.setDropCompleted(true);
            event.consume();
            onServerTransfered.accept(server);
        });
    }
}
