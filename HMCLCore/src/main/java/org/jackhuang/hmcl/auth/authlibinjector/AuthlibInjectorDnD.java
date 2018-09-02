/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.auth.authlibinjector;

import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author yushijinhun
 * @see https://github.com/yushijinhun/authlib-injector/wiki/%E5%90%AF%E5%8A%A8%E5%99%A8%E6%8A%80%E6%9C%AF%E8%A7%84%E8%8C%83#dnd-%E6%96%B9%E5%BC%8F%E6%B7%BB%E5%8A%A0-yggdrasil-%E6%9C%8D%E5%8A%A1%E7%AB%AF
 */
public final class AuthlibInjectorDnD {

    private static final String SCHEME = "authlib-injector";
    private static final String PATH_YGGDRASIL_SERVER = "yggdrasil-server";

    private AuthlibInjectorDnD() {}

    public static Optional<String> parseUrlFromDragboard(Dragboard dragboard) {
        String uri = dragboard.getString();
        if (uri == null) return Optional.empty();

        String[] uriElements = uri.split(":");
        if (uriElements.length == 3 && SCHEME.equals(uriElements[0]) && PATH_YGGDRASIL_SERVER.equals(uriElements[1])) {
            try {
                return Optional.of(URLDecoder.decode(uriElements[2], "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
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
