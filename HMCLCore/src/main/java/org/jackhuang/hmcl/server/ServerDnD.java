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

import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Consumer;

// minecraft-server:base64ServerName:base64ServerIP[:base64Favicon]
public final class ServerDnD {
    private static final String SCHEME = "minecraft-server";

    private ServerDnD() {
    }

    public static Optional<Server> parseUrlFromDragboard(Dragboard dragboard) {
        String url = dragboard.getString();
        if (url == null) return Optional.empty();


        String[] urlElements = url.split(":");

        if (urlElements.length != 4 && urlElements.length != 3) {
            return Optional.empty();
        }
        if (!urlElements[0].equals(SCHEME)) return Optional.empty();

        try {
            return Optional.of(new Server(
                    false,
                    false,
                    urlElements.length == 4 ? urlElements[3] : null,
                    new String(Base64.getDecoder().decode(urlElements[2]), StandardCharsets.UTF_8),
                    new String(Base64.getDecoder().decode(urlElements[1]), StandardCharsets.UTF_8)
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
