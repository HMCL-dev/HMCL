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
package org.jackhuang.hmcl.auth.authlibinjector;

import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.util.Optional;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.io.NetworkUtils.decodeURL;

/**
 * @author yushijinhun
 * @see <a href="https://yushijinhun.github.io/authlib-injector/en/launcher-technical-specification.html#configuration-via-drag-and-drop">Launcher Technical Specification for Authlib-Injector</a>
 */
public final class AuthlibInjectorDnD {

    private static final String SCHEME = "authlib-injector";
    private static final String PATH_YGGDRASIL_SERVER = "yggdrasil-server";

    private AuthlibInjectorDnD() {}

    public static Optional<String> parseUrlFromDragboard(Dragboard dragboard) {
        String url = dragboard.getString();
        if (url == null) return Optional.empty();

        String[] urlElements = url.split(":");
        if (urlElements.length == 3 && SCHEME.equals(urlElements[0]) && PATH_YGGDRASIL_SERVER.equals(urlElements[1])) {
            return Optional.of(decodeURL(urlElements[2]));
        }
        return Optional.empty();
    }

    public static EventHandler<DragEvent> dragOverHandler() {
        return event -> parseUrlFromDragboard(event.getDragboard()).ifPresent(url -> {
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
    }

    public static EventHandler<DragEvent> dragDroppedHandler(Consumer<String> onUrlTransfered) {
        return event -> parseUrlFromDragboard(event.getDragboard()).ifPresent(url -> {
            event.setDropCompleted(true);
            event.consume();
            onUrlTransfered.accept(url);
        });
    }

}
